package com.thewizrd.simplesleeptimer.viewmodels

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.ImageUtils
import com.thewizrd.shared_resources.viewmodels.MusicPlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MusicPlayersViewModel(private val app: Application) : AndroidViewModel(app),
    DataClient.OnDataChangedListener {
    private val viewModelState = MutableStateFlow(MusicPlayersUiState(isLoading = true))
    private var refreshJob: Job? = null

    val uiState = viewModelState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        viewModelState.value
    )

    init {
        Wearable.getDataClient(app.applicationContext)
            .addListener(this)
    }

    fun reloadMusicPlayers() {
        viewModelScope.launch {
            viewModelState.update {
                it.copy(isLoading = true)
            }

            LocalBroadcastManager.getInstance(app.applicationContext)
                .sendBroadcast(Intent(WearableHelper.MusicPlayersPath))
        }
    }

    fun loadMusicPlayers() {
        reloadMusicPlayers()

        refreshJob = viewModelScope.launch {
            delay(3000)
            if (isActive) {
                refreshMusicPlayers()
            }
        }
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        viewModelScope.launch {
            // Cancel job
            refreshJob?.cancel()

            for (event in dataEventBuffer) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val item = event.dataItem
                    if (WearableHelper.MusicPlayersPath == item.uri.path) {
                        try {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            updateMusicPlayers(dataMap)
                        } catch (e: Exception) {
                            Log.e("MusicPlayersViewModel", "Error", e)
                        }
                    }
                }
            }

            viewModelState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun refreshMusicPlayers() = withContext(Dispatchers.IO) {
        try {
            val buff = Wearable.getDataClient(app.applicationContext)
                .getDataItems(
                    WearableHelper.getWearDataUri(
                        "*",
                        WearableHelper.MusicPlayersPath
                    )
                )
                .await()

            for (i in 0 until buff.count) {
                val item = buff[i]
                if (WearableHelper.MusicPlayersPath == item.uri.path) {
                    try {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        updateMusicPlayers(dataMap)
                    } catch (e: Exception) {
                        Log.e("MusicPlayersViewModel", "Error", e)
                    }
                    viewModelState.update { it.copy(isLoading = false) }
                }
            }
            buff.release()
        } catch (e: Exception) {
            Log.e("MusicPlayersViewModel", "Error", e)
        }
    }

    private suspend fun updateMusicPlayers(dataMap: DataMap) {
        val supportedPlayers =
            dataMap.getStringArrayList(WearableHelper.KEY_SUPPORTEDPLAYERS) ?: return
        val viewModels = mutableListOf<MusicPlayerViewModel>()
        for (key in supportedPlayers) {
            val map = dataMap.getDataMap(key) ?: continue

            val model = MusicPlayerViewModel().apply {
                appLabel = map.getString(WearableHelper.KEY_LABEL)
                packageName = map.getString(WearableHelper.KEY_PKGNAME)
                activityName = map.getString(WearableHelper.KEY_ACTIVITYNAME)
                bitmapIcon = runCatching {
                    ImageUtils.bitmapFromAssetStream(
                        Wearable.getDataClient(app.applicationContext),
                        map.getAsset(WearableHelper.KEY_ICON)
                    )
                }.getOrNull()
            }

            viewModels.add(model)
        }

        viewModelState.update {
            it.copy(players = viewModels)
        }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
        Wearable.getDataClient(app.applicationContext)
            .removeListener(this)
    }
}

data class MusicPlayersUiState(
    val players: List<MusicPlayerViewModel> = emptyList(),
    val isLoading: Boolean = false
)