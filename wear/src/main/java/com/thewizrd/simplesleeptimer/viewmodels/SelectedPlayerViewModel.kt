package com.thewizrd.simplesleeptimer.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SelectedPlayerViewModel(private val app: Application) : AndroidViewModel(app) {
    private val selectedPlayerState = MutableStateFlow(SelectedPlayerState())

    val selectedPlayer = selectedPlayerState.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        selectedPlayerState.value
    )

    fun updateSelectedPlayer(key: String?) {
        selectedPlayerState.update {
            it.copy(key = key)
        }
    }

    fun getSelectedPlayerData() {
        viewModelScope.launch(Dispatchers.IO) {
            var prefKey: String? = null
            try {
                val buff = Wearable.getDataClient(app.applicationContext)
                    .getDataItems(
                        WearableHelper.getWearDataUri(
                            "*",
                            SleepTimerHelper.SleepTimerAudioPlayerPath
                        )
                    )
                    .await()

                for (i in 0 until buff.count) {
                    val item = buff[i]
                    if (SleepTimerHelper.SleepTimerAudioPlayerPath == item.uri.path) {
                        try {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            prefKey = dataMap.getString(SleepTimerHelper.KEY_SELECTEDPLAYER, "")
                        } catch (e: Exception) {
                            Log.e("SelectedPlayerViewModel", "Error", e)
                        }
                        break
                    }
                }
                buff.release()
            } catch (e: Exception) {
                Log.e("SelectedPlayerViewModel", "Error", e)
                prefKey = null
            }

            updateSelectedPlayer(prefKey)
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