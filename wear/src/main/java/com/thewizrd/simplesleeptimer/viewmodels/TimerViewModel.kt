package com.thewizrd.simplesleeptimer.viewmodels

import android.app.Application
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import androidx.annotation.RequiresApi
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.bytesToBool
import com.thewizrd.shared_resources.utils.bytesToLong
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.shared_resources.utils.intToBytes
import com.thewizrd.shared_resources.utils.stringToBytes
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.preferences.Settings
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class TimerViewModel(app: Application) : WearableListenerViewModel(app) {
    companion object {
        private val MAX_TIME_IN_MINS = TimeUnit.HOURS.toMinutes(24)
        private val MAX_TIME_IN_MILLIS = TimeUnit.MINUTES.toMillis(MAX_TIME_IN_MINS)
        private val ONE_MIN_IN_MILLIS = TimeUnit.MINUTES.toMillis(1)
    }

    private val viewModelState = MutableStateFlow(TimerUiState())
    private val timerEventFlow = MutableSharedFlow<TimerOperation>(
        replay = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val uiState = viewModelState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        viewModelState.value
    )

    val timerEvents = timerEventFlow.shareIn(
        viewModelScope,
        SharingStarted.Lazily,
        0
    )

    init {
        viewModelScope.launch {
            eventFlow.collect { event ->
                when (event.eventType) {

                }
            }
        }
    }

    fun updateTimerState(
        isRunning: Boolean? = null,
        timerLengthInMs: Long? = null,
        remainingTimeInMs: Long? = null,
        isLocalTimer: Boolean? = null,
        isLoading: Boolean? = null,
    ) {
        viewModelState.update {
            it.copy(
                isRunning = isRunning ?: it.isRunning,
                timerLengthInMs = timerLengthInMs ?: it.timerLengthInMs,
                remainingTimeInMs = remainingTimeInMs ?: it.remainingTimeInMs,
                isLocalTimer = isLocalTimer ?: it.isLocalTimer,
                isLoading = isLoading ?: it.isLoading,
            )
        }
    }

    fun updateTimerState(timerModel: TimerModel) {
        viewModelState.update {
            it.copy(
                isRunning = timerModel.isRunning,
                timerLengthInMs = timerModel.timerLengthInMs,
                remainingTimeInMs = timerModel.remainingTimeInMs
            )
        }
    }

    fun requestTimerOp(op: TimerOperation) {
        viewModelScope.launch {
            timerEventFlow.emit(op)
        }

        when (op) {
            TimerOperation.ADD_1M -> {
                viewModelState.update {
                    it.copy(
                        timerLengthInMs = MAX_TIME_IN_MILLIS
                            .coerceAtMost(it.timerLengthInMs + ONE_MIN_IN_MILLIS)
                    )
                }
            }

            TimerOperation.ADD_5M -> {
                viewModelState.update {
                    it.copy(
                        timerLengthInMs = MAX_TIME_IN_MILLIS
                            .coerceAtMost(
                                it.timerLengthInMs + ONE_MIN_IN_MILLIS.times(5)
                            )
                    )
                }
            }

            TimerOperation.MINUS_1M -> {
                viewModelState.update {
                    it.copy(
                        timerLengthInMs = (it.timerLengthInMs - ONE_MIN_IN_MILLIS)
                            .coerceAtLeast(0)
                    )
                }
            }

            TimerOperation.MINUS_5M -> {
                viewModelState.update {
                    it.copy(
                        timerLengthInMs = (it.timerLengthInMs - ONE_MIN_IN_MILLIS.times(5))
                            .coerceAtLeast(0)
                    )
                }
            }

            TimerOperation.STOP -> {
                // Reset timer length
                viewModelState.update {
                    it.copy(timerLengthInMs = Settings.getLastTimeSet() * ONE_MIN_IN_MILLIS)
                }
            }

            else -> {
                // ignore
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun notifyAlarmPermissionDenied() {
        _eventsFlow.tryEmit(
            WearableEvent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        )
    }

    /* Remote Timer */
    fun refreshStatus() {
        viewModelState.update {
            it.copy(isLoading = true)
        }

        viewModelScope.launch {
            updateConnectionStatus()
            requestTimerStatus()
        }
    }

    private suspend fun requestTimerStatus() {
        if (connect()) {
            sendMessage(mPhoneNodeWithApp!!.id, SleepTimerHelper.SleepTimerStatusPath, null)
        }
    }

    fun requestSleepTimerStop() {
        viewModelScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, SleepTimerHelper.SleepTimerStopPath, null)
            }
        }
    }

    fun requestUpdateTimer(model: TimerModel) {
        viewModelScope.launch {
            if (connect()) {
                sendMessage(
                    mPhoneNodeWithApp!!.id, SleepTimerHelper.SleepTimerUpdateStatePath,
                    JSONParser.serializer(model, TimerModel::class.java).stringToBytes()
                )
            }
        }
    }

    fun requestSleepTimerStart(timeInMins: Int, selectedPlayer: SelectedPlayerState? = null) {
        viewModelScope.launch {
            if (connect()) {
                sendMessage(
                    mPhoneNodeWithApp!!.id, SleepTimerHelper.SleepTimerStartPath,
                    timeInMins.intToBytes()
                )

                if (selectedPlayer?.isValid == true) {
                    val intent = WearableHelper.createRemoteActivityIntent(
                        selectedPlayer.packageName!!,
                        selectedPlayer.activityName!!
                    )
                    val success = startRemoteActivity(intent)

                    if (!success) {
                        _eventsFlow.tryEmit(
                            WearableEvent(
                                ACTION_SHOWCONFIRMATION,
                                Bundle().apply {
                                    putString(
                                        EXTRA_EVENTDATA,
                                        JSONParser.serializer(
                                            ConfirmationData.failure(), ConfirmationData::class.java
                                        )
                                    )
                                }
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun requestPhoneAppVersion(): Long {
        return suspendCancellableCoroutine { continuation ->
            val listener = MessageClient.OnMessageReceivedListener { event ->
                when (event.path) {
                    WearableHelper.VersionPath -> {
                        if (continuation.isActive) {
                            val versionCode = event.data.bytesToLong()
                            continuation.resume(versionCode)
                        }
                    }
                }
            }

            continuation.invokeOnCancellation {
                Wearable.getMessageClient(appContext)
                    .removeListener(listener)
            }

            viewModelScope.launch {
                Wearable.getMessageClient(appContext)
                    .addListener(
                        listener,
                        WearableHelper.getWearDataUri("*", WearableHelper.VersionPath),
                        MessageClient.FILTER_LITERAL
                    ).await()

                if (connect()) {
                    sendMessage(mPhoneNodeWithApp!!.id, WearableHelper.VersionPath, null)
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (!uiState.value.isLocalTimer) {
            when (messageEvent.path) {
                SleepTimerHelper.SleepTimerStatusPath, SleepTimerHelper.SleepTimerStartPath -> {
                    val data = messageEvent.data.bytesToString()
                    _eventsFlow.tryEmit(
                        WearableEvent(
                            messageEvent.path,
                            Bundle().apply {
                                putString(EXTRA_EVENTDATA, data)
                            }
                        )
                    )
                }

                SleepTimerHelper.SleepTimerStopPath -> {
                    _eventsFlow.tryEmit(WearableEvent(messageEvent.path))
                }

                WearableHelper.OpenMusicPlayerPath -> {
                    val success = messageEvent.data.bytesToBool()
                    if (!success) {
                        _eventsFlow.tryEmit(
                            WearableEvent(
                                ACTION_SHOWCONFIRMATION,
                                Bundle().apply {
                                    putString(
                                        EXTRA_EVENTDATA,
                                        JSONParser.serializer(
                                            ConfirmationData(
                                                title = appContext.getString(R.string.error_permissiondenied)
                                            ), ConfirmationData::class.java
                                        )
                                    )
                                }
                            )
                        )

                        viewModelScope.launch {
                            sendMessage(
                                messageEvent.sourceNodeId,
                                WearableHelper.StartPermissionsActivityPath,
                                null
                            )
                        }
                    }
                }
            }
        } else {
            super.onMessageReceived(messageEvent)
        }
    }
}

data class TimerUiState(
    val isRunning: Boolean = false,
    val timerLengthInMs: Long = TimerModel.DEFAULT_TIME_MIN * DateUtils.MINUTE_IN_MILLIS,
    val remainingTimeInMs: Long = 0,
    val isLocalTimer: Boolean = true,
    val isLoading: Boolean = false
) {
    fun getTimerProgress(): Float = 1f - (remainingTimeInMs.toFloat() / timerLengthInMs)

    fun getTimerLengthInMins(): Int = TimeUnit.MILLISECONDS.toMinutes(timerLengthInMs).toInt()
}

enum class TimerOperation {
    START,
    STOP,
    EXTEND_1M,
    EXTEND_5M,
    ADD_1M,
    ADD_5M,
    MINUS_1M,
    MINUS_5M
}