package com.thewizrd.simplesleeptimer.viewmodels

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.ChannelClient.Channel
import com.google.android.gms.wearable.ChannelClient.ChannelCallback
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.media.MusicPlayersData
import com.thewizrd.shared_resources.utils.ImageUtils.toBitmap
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.bytesToBool
import com.thewizrd.shared_resources.viewmodels.MusicPlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.tasks.await

data class MusicPlayersUiState(
    val players: List<MusicPlayerViewModel> = emptyList(),
    val isLoading: Boolean = false
)

class MusicPlayersViewModel(app: Application) : WearableListenerViewModel(app) {
    private val viewModelState = MutableStateFlow(MusicPlayersUiState(isLoading = true))

    val uiState = viewModelState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        viewModelState.value
    )

    private val channelCallback = object : ChannelCallback() {
        override fun onChannelOpened(channel: Channel) {
            startChannelListener(channel)
        }

        override fun onChannelClosed(
            channel: Channel,
            closeReason: Int,
            appSpecificErrorCode: Int
        ) {
            Logger.debug(
                "ChannelCallback",
                "channel closed - reason = $closeReason | path = ${channel.path}"
            )
        }
    }

    init {
        Wearable.getChannelClient(appContext).run {
            registerChannelCallback(channelCallback)
        }

        viewModelScope.launch {
            channelEventsFlow.collect { event ->
                when (event.eventType) {
                    WearableHelper.MusicPlayersPath -> {
                        val jsonData = event.data.getString(EXTRA_EVENTDATA)

                        viewModelScope.launch {
                            val playersData = jsonData?.let {
                                JSONParser.deserializer(it, MusicPlayersData::class.java)
                            }

                            updateMusicPlayers(playersData)
                        }
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearableHelper.MusicPlayersPath -> {
                val success = messageEvent.data.bytesToBool()

                if (!success) {
                    viewModelState.update {
                        it.copy(
                            players = emptyList()
                        )
                    }
                }

                _eventsFlow.tryEmit(WearableEvent(messageEvent.path, Bundle().apply {
                    putBoolean(EXTRA_STATUS, success)
                }))
            }

            else -> super.onMessageReceived(messageEvent)
        }
    }

    private fun startChannelListener(channel: Channel) {
        when (channel.path) {
            WearableHelper.MusicPlayersPath -> {
                createChannelListener(channel)
            }
        }
    }

    private fun createChannelListener(channel: Channel): Job =
        viewModelScope.launch(Dispatchers.Default) {
            supervisorScope {
                runCatching {
                    val stream = Wearable.getChannelClient(appContext)
                        .getInputStream(channel).await()
                    stream.bufferedReader().use { reader ->
                        val line = reader.readLine()

                        when {
                            line.startsWith("data: ") -> {
                                runCatching {
                                    val json = line.substringAfter("data: ")
                                    _channelEventsFlow.tryEmit(
                                        WearableEvent(channel.path, Bundle().apply {
                                            putString(EXTRA_EVENTDATA, json)
                                        })
                                    )
                                }.onFailure {
                                    Logger.error(
                                        "MusicPlayersChannelListener",
                                        it,
                                        "error reading data for channel = ${channel.path}"
                                    )
                                }
                            }

                            line.isEmpty() -> {
                                // empty line; data terminator
                            }

                            else -> {}
                        }
                    }
                }.onFailure {
                    Logger.error("MusicPlayersChannelListener", it, "error")
                }
            }
        }

    override fun onCleared() {
        Wearable.getChannelClient(appContext).run {
            unregisterChannelCallback(channelCallback)
        }
        super.onCleared()
    }

    fun loadMusicPlayers() {
        reloadMusicPlayers()
    }

    private fun reloadMusicPlayers() {
        viewModelScope.launch {
            viewModelState.update {
                it.copy(isLoading = true)
            }

            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, WearableHelper.MusicPlayersPath, null)
            }
        }
    }

    private suspend fun updateMusicPlayers(playersData: MusicPlayersData?) {
        val musicPlayersList = playersData?.musicPlayers?.mapTo(mutableListOf()) { player ->
            MusicPlayerViewModel().apply {
                appLabel = player.label
                packageName = player.packageName
                activityName = player.activityName
                bitmapIcon = player.iconBitmap?.toBitmap()
            }
        }

        viewModelState.update {
            it.copy(
                players = musicPlayersList ?: emptyList(),
                isLoading = false
            )
        }
    }
}