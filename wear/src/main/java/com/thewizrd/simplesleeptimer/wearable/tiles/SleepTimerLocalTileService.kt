package com.thewizrd.simplesleeptimer.wearable.tiles

import android.content.Context
import android.util.Log
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.SuspendingTileService
import com.thewizrd.shared_resources.services.BaseTimerService
import com.thewizrd.shared_resources.sleeptimer.TimerDataModel
import com.thewizrd.simplesleeptimer.services.TimerService
import com.thewizrd.simplesleeptimer.utils.connectService

@OptIn(ExperimentalHorologistApi::class)
class SleepTimerLocalTileService : SuspendingTileService() {
    companion object {
        fun requestTileUpdate(context: Context) {
            getUpdater(context).requestUpdate(SleepTimerLocalTileService::class.java)
        }
    }

    private val tileRenderer = SleepTimerTileRenderer(this)

    override fun onCreate() {
        super.onCreate()
        Log.d(this::class.java.simpleName, "creating service...")
    }

    override fun onDestroy() {
        Log.d(this::class.java.simpleName, "destroying service...")
        super.onDestroy()
    }

    override fun onTileEnterEvent(requestParams: EventBuilders.TileEnterEvent) {
        super.onTileEnterEvent(requestParams)
    }

    override suspend fun tileRequest(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        val connectionPair = this.connectService<TimerService, BaseTimerService.LocalBinder>()
        val timerBinder = connectionPair.first
        val serviceConnection = connectionPair.second

        if (requestParams.currentState.lastClickableId.isNotBlank()) {
            when (requestParams.currentState.lastClickableId) {
                SleepTimerTileRenderer.ID_5MIN -> timerBinder.startTimer(5)
                SleepTimerTileRenderer.ID_10MIN -> timerBinder.startTimer(10)
                SleepTimerTileRenderer.ID_15MIN -> timerBinder.startTimer(15)
                SleepTimerTileRenderer.ID_20MIN -> timerBinder.startTimer(20)
                SleepTimerTileRenderer.ID_30MIN -> timerBinder.startTimer(30)
            }
        }

        runCatching {
            unbindService(serviceConnection)
        }

        val model = TimerDataModel.getDataModel().toModel()
        val state = TimerTileState(true, model)

        return tileRenderer.renderTimeline(state, requestParams)
    }

    override suspend fun resourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ResourceBuilders.Resources {
        return tileRenderer.produceRequestedResources(Unit, requestParams)
    }
}