@file:OptIn(ExperimentalHorologistApi::class)

package com.thewizrd.simplesleeptimer.wearable.tiles

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.lifecycle.lifecycleScope
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.SuspendingTileService
import com.thewizrd.shared_resources.appLib
import com.thewizrd.shared_resources.utils.AnalyticsLogger
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplesleeptimer.datastore.remoteTimerDataStore
import com.thewizrd.simplesleeptimer.wearable.tiles.SleepTimerTileRenderer.Companion.ID_10MIN
import com.thewizrd.simplesleeptimer.wearable.tiles.SleepTimerTileRenderer.Companion.ID_15MIN
import com.thewizrd.simplesleeptimer.wearable.tiles.SleepTimerTileRenderer.Companion.ID_20MIN
import com.thewizrd.simplesleeptimer.wearable.tiles.SleepTimerTileRenderer.Companion.ID_30MIN
import com.thewizrd.simplesleeptimer.wearable.tiles.SleepTimerTileRenderer.Companion.ID_5MIN
import com.thewizrd.simplesleeptimer.wearable.tiles.SleepTimerTileRenderer.Companion.ID_STOP
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

class SleepTimerTileService : SuspendingTileService() {
    companion object {
        private const val TAG = "SleepTimerTileService"

        fun requestTileUpdate(context: Context) {
            updateJob?.cancel()

            updateJob = appLib.appScope.launch {
                delay(1000)
                if (isActive) {
                    Logger.debug(TAG, "requesting tile update")
                    getUpdater(context).requestUpdate(SleepTimerTileService::class.java)
                }
            }
        }

        @JvmStatic
        @Volatile
        var isInFocus: Boolean = false
            private set

        @JvmStatic
        @Volatile
        var isUpdating: Boolean = false
            private set

        private var updateJob: Job? = null
    }

    private lateinit var tileMessenger: TimerTileMessenger
    private lateinit var tileStateFlow: StateFlow<RemoteTimerTileState?>
    private lateinit var tileRenderer: SleepTimerTileRenderer

    override fun onCreate() {
        super.onCreate()
        Logger.debug(TAG, "creating service...")

        tileRenderer = SleepTimerTileRenderer(this)
        tileMessenger = TimerTileMessenger(this)

        tileMessenger.register()
        tileStateFlow = this.remoteTimerDataStore.data
            .combine(tileMessenger.connectionState) { cache, connectionStatus ->

                RemoteTimerTileState(
                    connectionStatus = connectionStatus,
                    isLocalTimer = cache.isLocalTimer,
                    timerModel = cache.timerModel,
                )
            }
            .stateIn(
                lifecycleScope,
                started = SharingStarted.WhileSubscribed(2000),
                initialValue = null
            )
    }

    override fun onDestroy() {
        isUpdating = false
        Logger.debug(TAG, "destroying service...")
        tileMessenger.unregister()
        super.onDestroy()
    }

    override fun onTileEnterEvent(requestParams: EventBuilders.TileEnterEvent) {
        super.onTileEnterEvent(requestParams)

        Logger.debug(TAG, "onTileEnterEvent called with: tileId = ${requestParams.tileId}")
        AnalyticsLogger.logEvent("on_tile_enter", Bundle().apply {
            putString("tile", TAG)
        })
        isInFocus = true

        lifecycleScope.launch {
            tileMessenger.checkConnectionStatus()
            tileMessenger.requestUpdate()
        }.invokeOnCompletion {
            if (it is CancellationException || !isUpdating) {
                // If update timed out
                requestTileUpdate(this)
            }
        }
    }

    override fun onTileLeaveEvent(requestParams: EventBuilders.TileLeaveEvent) {
        super.onTileLeaveEvent(requestParams)
        Logger.debug(TAG, "$TAG: onTileLeaveEvent called with: tileId = ${requestParams.tileId}")
        isInFocus = false
    }

    override suspend fun tileRequest(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        Logger.debug(TAG, "tileRequest: ${requestParams.currentState}")
        val startTime = SystemClock.elapsedRealtimeNanos()
        isUpdating = true

        tileMessenger.checkConnectionStatus()

        if (requestParams.currentState.lastClickableId.isNotBlank()) {
            when (requestParams.currentState.lastClickableId) {
                ID_5MIN -> tileMessenger.requestTimerStart(5)
                ID_10MIN -> tileMessenger.requestTimerStart(10)
                ID_15MIN -> tileMessenger.requestTimerStart(15)
                ID_20MIN -> tileMessenger.requestTimerStart(20)
                ID_30MIN -> tileMessenger.requestTimerStart(30)
                ID_STOP -> tileMessenger.requestTimerStop()
            }
        }

        isUpdating = false
        val tileState = latestTileState()

        if (tileState.isEmpty) {
            AnalyticsLogger.logEvent("dashtile_state_empty", Bundle().apply {
                putBoolean("isCoroutineActive", coroutineContext.isActive)
            })
        }

        val endTime = SystemClock.elapsedRealtimeNanos()
        Logger.debug(TAG, "Duration - ${Duration.ofNanos(endTime - startTime)}")
        Logger.debug(TAG, "Rendering timeline...")
        return tileRenderer.renderTimeline(tileState, requestParams)
    }

    private suspend fun latestTileState(): RemoteTimerTileState {
        var tileState = tileStateFlow.filterNotNull().first()

        if (tileState.isEmpty) {
            Logger.debug(TAG, "No tile state available. loading from remote...")
            tileMessenger.requestUpdate()

            // Try to await for full metadata change
            runCatching {
                withTimeoutOrNull(5000) {
                    supervisorScope {
                        tileStateFlow.filterNotNull().collectLatest { newState ->
                            if (!newState.isEmpty) {
                                tileState = newState
                                coroutineContext.cancel()
                            }
                        }
                    }
                }
            }
        }

        return tileState
    }

    override suspend fun resourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ResourceBuilders.Resources {
        return tileRenderer.produceRequestedResources(Unit, requestParams)
    }
}