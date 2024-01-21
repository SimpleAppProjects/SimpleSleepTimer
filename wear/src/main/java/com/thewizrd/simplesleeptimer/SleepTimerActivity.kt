package com.thewizrd.simplesleeptimer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.MessageEvent
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.services.BaseTimerService
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.bytesToBool
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.shared_resources.utils.intToBytes
import com.thewizrd.shared_resources.utils.stringToBytes
import com.thewizrd.simplesleeptimer.controls.CustomConfirmationOverlay
import com.thewizrd.simplesleeptimer.helpers.showConfirmationOverlay
import com.thewizrd.simplesleeptimer.preferences.Settings
import com.thewizrd.simplesleeptimer.ui.SleepTimerApp
import com.thewizrd.simplesleeptimer.viewmodels.SelectedPlayerViewModel
import com.thewizrd.simplesleeptimer.viewmodels.TimerOperation
import com.thewizrd.simplesleeptimer.viewmodels.TimerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Sleep Timer remote control activity for connected device
 */
class SleepTimerActivity : WearableListenerActivity() {
    override lateinit var broadcastReceiver: BroadcastReceiver
        private set
    override lateinit var intentFilter: IntentFilter
        private set

    private val timerViewModel: TimerViewModel by viewModels()
    private val selectedPlayerViewModel: SelectedPlayerViewModel by viewModels()
    private val timeKeeperModel: TimerModel by viewModels()
    private var timerUpdateJob: Job? = null

    private var handledIntent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installSplashScreen()

        timerViewModel.updateTimerState(isLocalTimer = false, isLoading = true)

        setContent {
            SleepTimerApp()
        }

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                lifecycleScope.launch {
                    if (intent.action != null) {
                        when (intent.action) {
                            ACTION_UPDATECONNECTIONSTATUS -> {
                                when (WearConnectionStatus.valueOf(
                                    intent.getIntExtra(
                                        EXTRA_CONNECTIONSTATUS,
                                        0
                                    )
                                )) {
                                    WearConnectionStatus.DISCONNECTED -> {
                                        // Navigate
                                        startActivity(
                                            Intent(
                                                this@SleepTimerActivity,
                                                PhoneSyncActivity::class.java
                                            )
                                        )
                                        finishAffinity()
                                    }
                                    WearConnectionStatus.APPNOTINSTALLED -> {
                                        val intentapp = Intent(Intent.ACTION_VIEW)
                                            .addCategory(Intent.CATEGORY_BROWSABLE)
                                            .setData(SleepTimerHelper.getPlayStoreURI())

                                        lifecycleScope.launch {
                                            runCatching {
                                                remoteActivityHelper.startRemoteActivity(intentapp)
                                                    .await()

                                                showConfirmationOverlay(true)
                                            }.onFailure {
                                                if (it !is CancellationException) {
                                                    showConfirmationOverlay(false)
                                                }
                                            }
                                        }
                                    }
                                    WearConnectionStatus.CONNECTED -> {
                                        launch {
                                            delay(1000)
                                            timerViewModel.updateTimerState(
                                                isLoading = false,
                                                isRunning = timeKeeperModel.isRunning
                                            )
                                        }
                                    }

                                    else -> {}
                                }
                            }
                            WearableHelper.MusicPlayersPath -> {
                                if (connect()) {
                                    sendMessage(
                                        mPhoneNodeWithApp!!.id,
                                        WearableHelper.MusicPlayersPath,
                                        null
                                    )
                                }
                            }
                            else -> {
                                Log.println(
                                    Log.INFO,
                                    "SleepTimerActivity",
                                    "Unhandled action: ${intent.action}"
                                )
                            }
                        }
                    }
                }
            }
        }

        intentFilter = IntentFilter().apply {
            addAction(ACTION_UPDATECONNECTIONSTATUS)
            addAction(WearableHelper.MusicPlayersPath)
        }

        handleIntent(intent)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        lifecycleScope.launch {
            when (messageEvent.path) {
                SleepTimerHelper.SleepTimerStatusPath, SleepTimerHelper.SleepTimerStartPath -> {
                    val data = JSONParser.deserializer(
                        messageEvent.data.bytesToString(),
                        TimerModel::class.java
                    )

                    data?.let {
                        // Add a second for latency
                        it.endTimeInMs = it.endTimeInMs + DateUtils.SECOND_IN_MILLIS

                        if (timeKeeperModel.isRunning || it.isRunning != timeKeeperModel.isRunning) {
                            timeKeeperModel.updateModel(it)
                            if (!it.isRunning && timeKeeperModel.timerLengthInMins <= 0) {
                                timeKeeperModel.timerLengthInMins = Settings.getLastTimeSet()
                            }
                        }

                        timerViewModel.updateTimerState(timeKeeperModel)
                    } ?: return@launch

                    if (timeKeeperModel.isRunning) {
                        showTimerProgressView()
                    } else {
                        showTimerStartView()
                    }
                }
                SleepTimerHelper.SleepTimerStopPath -> {
                    timeKeeperModel.stopTimer()
                    showTimerStartView()
                }
                WearableHelper.OpenMusicPlayerPath -> {
                    val success = messageEvent.data.bytesToBool()
                    if (!success) {
                        CustomConfirmationOverlay()
                            .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                            .setCustomDrawable(
                                ContextCompat.getDrawable(
                                    this@SleepTimerActivity,
                                    R.drawable.ws_full_sad
                                )
                            )
                            .setMessage(this@SleepTimerActivity.getString(R.string.error_permissiondenied))
                            .showOn(this@SleepTimerActivity)

                        launch {
                            sendMessage(
                                messageEvent.sourceNodeId,
                                WearableHelper.StartPermissionsActivityPath,
                                null
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (PermissionChecker.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PermissionChecker.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }

        lifecycleScope.launch {
            timerViewModel.timerEvents.collect {
                when (it) {
                    TimerOperation.START -> {
                        timeKeeperModel.timerLengthInMs =
                            timerViewModel.uiState.value.timerLengthInMs

                        requestSleepTimerStart()
                    }

                    TimerOperation.STOP -> requestSleepTimerStop()

                    TimerOperation.EXTEND_1M -> {
                        timeKeeperModel.extend1Min()
                        requestUpdateTimer()
                    }

                    TimerOperation.EXTEND_5M -> {
                        timeKeeperModel.extend5Min()
                        requestUpdateTimer()
                    }

                    else -> { /* ignore */
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        handledIntent = true

        if (intent?.hasExtra(BaseTimerService.EXTRA_TIME_IN_MINS) == true) {
            lifecycleScope.launch {
                supervisorScope {
                    timerViewModel.uiState.filterNot {
                        it.isLoading
                    }.collectLatest { state ->
                        intent.getIntExtra(BaseTimerService.EXTRA_TIME_IN_MINS, 0).takeIf { it > 0 }
                            ?.let {
                                if (!state.isRunning) {
                                    timeKeeperModel.timerLengthInMins = it
                                    requestSleepTimerStart()
                                }

                                // Cancel flow subscription
                                this.cancel()
                            }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Update statuses
        timerViewModel.updateTimerState(isLoading = true)

        lifecycleScope.launch {
            updateConnectionStatus()
            requestTimerStatus()
        }
    }

    override fun onStop() {
        super.onStop()
        stopUpdatingTime()
        timeKeeperModel.stopTimer()
    }

    private suspend fun requestTimerStatus() {
        if (connect()) {
            sendMessage(mPhoneNodeWithApp!!.id, SleepTimerHelper.SleepTimerStatusPath, null)
        }
    }

    private fun requestSleepTimerStop() {
        lifecycleScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, SleepTimerHelper.SleepTimerStopPath, null)
            }
        }
    }

    private fun requestSleepTimerStart() {
        lifecycleScope.launch {
            if (connect()) {
                val selectedPlayer = selectedPlayerViewModel.selectedPlayer.value

                sendMessage(
                    mPhoneNodeWithApp!!.id, SleepTimerHelper.SleepTimerStartPath,
                    timeKeeperModel.timerLengthInMins.intToBytes()
                )

                if (selectedPlayer.isValid) {
                    runCatching {
                        val intent = WearableHelper.createRemoteActivityIntent(
                            selectedPlayer.packageName!!,
                            selectedPlayer.activityName!!
                        )
                        remoteActivityHelper.startRemoteActivity(intent).await()
                    }.onFailure {
                        Log.e(this::class.java.simpleName, "Error starting remote activity", it)

                        CustomConfirmationOverlay()
                            .setType(CustomConfirmationOverlay.FAILURE_ANIMATION)
                            .showOn(this@SleepTimerActivity)
                    }
                }
            }
        }
    }

    private fun requestUpdateTimer() {
        lifecycleScope.launch {
            if (connect()) {
                sendMessage(
                    mPhoneNodeWithApp!!.id, SleepTimerHelper.SleepTimerUpdateStatePath,
                    JSONParser.serializer(timeKeeperModel, TimerModel::class.java).stringToBytes()
                )
            }
        }
    }

    private fun showTimerStartView() {
        stopUpdatingTime()
        timerViewModel.updateTimerState(timeKeeperModel)
    }

    private fun showTimerProgressView() {
        timerViewModel.updateTimerState(timeKeeperModel)
        startUpdatingTime()
    }

    private fun startUpdatingTime() {
        stopUpdatingTime()
        timerUpdateJob = lifecycleScope.launch {
            while (isActive) {
                // If no timers require continuous updates, avoid scheduling the next update.
                if (!timeKeeperModel.isRunning) {
                    break
                } else {
                    timerViewModel.updateTimerState(timeKeeperModel)
                }

                // Repeat for progress animation
                delay(50)
            }
        }
    }

    private fun stopUpdatingTime() {
        timerUpdateJob?.cancel()
    }
}