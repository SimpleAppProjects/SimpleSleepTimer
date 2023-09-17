package com.thewizrd.simplesleeptimer.wearable.tiles

import android.content.Context
import androidx.lifecycle.lifecycleScope
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.SuspendingTileService
import com.thewizrd.simplesleeptimer.wearable.tiles.SleepTimerTileRenderer.Companion.ID_10MIN
import com.thewizrd.simplesleeptimer.wearable.tiles.SleepTimerTileRenderer.Companion.ID_15MIN
import com.thewizrd.simplesleeptimer.wearable.tiles.SleepTimerTileRenderer.Companion.ID_20MIN
import com.thewizrd.simplesleeptimer.wearable.tiles.SleepTimerTileRenderer.Companion.ID_30MIN
import com.thewizrd.simplesleeptimer.wearable.tiles.SleepTimerTileRenderer.Companion.ID_5MIN
import kotlinx.coroutines.launch

@OptIn(ExperimentalHorologistApi::class)
class SleepTimerTileService : SuspendingTileService() {
    companion object {
        fun requestTileUpdate(context: Context) {
            getUpdater(context).requestUpdate(SleepTimerTileService::class.java)
        }
    }

    private val tileRenderer = SleepTimerTileRenderer(this)
    private val tileMessenger = TimerTileMessenger(this)

    override fun onCreate() {
        super.onCreate()
        tileMessenger.register()
    }

    override fun onDestroy() {
        tileMessenger.unregister()
        super.onDestroy()
    }

    override fun onTileEnterEvent(requestParams: EventBuilders.TileEnterEvent) {
        super.onTileEnterEvent(requestParams)

        lifecycleScope.launch {
            tileMessenger.checkConnectionStatus()
        }
    }

    override suspend fun tileRequest(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        if (requestParams.currentState.lastClickableId.isNotBlank()) {
            when (requestParams.currentState.lastClickableId) {
                ID_5MIN -> tileMessenger.requestTimerStart(5)
                ID_10MIN -> tileMessenger.requestTimerStart(10)
                ID_15MIN -> tileMessenger.requestTimerStart(15)
                ID_20MIN -> tileMessenger.requestTimerStart(20)
                ID_30MIN -> tileMessenger.requestTimerStart(30)
            }
        }

        tileMessenger.checkConnectionStatus()
        val state = tileMessenger.getRemoteTimerStatus()

        return tileRenderer.renderTimeline(state, requestParams)
    }

    override suspend fun resourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ResourceBuilders.Resources {
        return tileRenderer.produceRequestedResources(Unit, requestParams)
    }
}