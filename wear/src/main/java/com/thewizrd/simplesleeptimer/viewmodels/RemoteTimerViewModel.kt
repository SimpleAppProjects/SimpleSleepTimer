package com.thewizrd.simplesleeptimer.viewmodels

import androidx.lifecycle.viewModelScope
import com.thewizrd.shared_resources.viewmodels.MusicPlayerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class RemoteTimerViewModel : TimerViewModel() {
    private val selectedPlayerFlow = MutableStateFlow(SelectedPlayerState())
    private val musicPlayersFlow = MutableStateFlow<List<MusicPlayerViewModel>>(emptyList())

    val selectedPlayerState = selectedPlayerFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        selectedPlayerFlow.value
    )

    val musicPlayersState = musicPlayersFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        musicPlayersFlow.value
    )

    fun updateSelectedPlayer(key: String?) {
        selectedPlayerFlow.update {
            it.copy(key = key)
        }
    }

    fun updateMusicPlayers(players: List<MusicPlayerViewModel>) {
        musicPlayersFlow.update { players }
    }

    init {

    }

    override fun onCleared() {
        super.onCleared()
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
}