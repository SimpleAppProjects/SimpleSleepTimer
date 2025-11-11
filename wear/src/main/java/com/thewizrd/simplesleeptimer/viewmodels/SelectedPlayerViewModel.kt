package com.thewizrd.simplesleeptimer.viewmodels

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.MessageEvent
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.shared_resources.utils.stringToBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SelectedPlayerViewModel(app: Application) : WearableListenerViewModel(app) {
    private val selectedPlayerState = MutableStateFlow(SelectedPlayerState())

    val selectedPlayer = selectedPlayerState.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        selectedPlayerState.value
    )

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            SleepTimerHelper.SleepTimerAudioPlayerPath -> {
                val prefKey = messageEvent.data.bytesToString()
                updateSelectedPlayer(prefKey)
            }

            else -> super.onMessageReceived(messageEvent)
        }
    }

    fun updateSelectedPlayer(key: String?) {
        selectedPlayerState.update {
            it.copy(key = key)
        }
    }

    fun getSelectedPlayerData() {
        viewModelScope.launch {
            if (connect()) {
                sendMessage(
                    mPhoneNodeWithApp!!.id,
                    SleepTimerHelper.SleepTimerAudioPlayerPath,
                    null
                )
            }
        }
    }

    suspend fun sendSelectedPlayerUpdate(key: String?) {
        if (connect()) {
            sendMessage(
                mPhoneNodeWithApp!!.id,
                SleepTimerHelper.SleepTimerUpdateAudioPlayerPath,
                key?.stringToBytes()
            )
        }
    }
}

data class SelectedPlayerState(
    val key: String? = null
) {
    var packageName: String? = null
        private set

    var activityName: String? = null
        private set

    init {
        val data = key?.split("/")?.toTypedArray()

        if (!data.isNullOrEmpty() && data.size == 2) {
            packageName = data[0]
            activityName = data[1]
        }
    }

    val isValid: Boolean
        get() = !packageName.isNullOrBlank() && !activityName.isNullOrBlank()
}