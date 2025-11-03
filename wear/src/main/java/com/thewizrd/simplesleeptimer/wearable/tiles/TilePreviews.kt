@file:Suppress("FunctionName")

package com.thewizrd.simplesleeptimer.wearable.tiles

import android.content.Context
import androidx.wear.tiles.tooling.preview.TilePreviewData
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.simplesleeptimer.ui.tiles.tools.WearPreviewDevices

@WearPreviewDevices
fun StartLocalTimerLayoutPreview(context: Context): TilePreviewData {
    val state = TimerTileState(
        isLocalTimer = true,
        timerModel = TimerModel().apply {
            timerLengthInMins = 5
            isRunning = false
        }
    )
    val renderer = SleepTimerTileRenderer(context, debugResourceMode = true)

    return TilePreviewData(
        onTileRequest = { renderer.renderTimeline(state, it) },
        onTileResourceRequest = { renderer.produceRequestedResources(Unit, it) }
    )
}

@WearPreviewDevices
fun DisconnectedRemoteTimerLayoutPreview(context: Context): TilePreviewData {
    val state = RemoteTimerTileState(
        connectionStatus = WearConnectionStatus.DISCONNECTED,
        isLocalTimer = true,
        timerModel = TimerModel().apply {
            timerLengthInMins = 5
            isRunning = false
        }
    )
    val renderer = SleepTimerTileRenderer(context, debugResourceMode = true)

    return TilePreviewData(
        onTileRequest = { renderer.renderTimeline(state, it) },
        onTileResourceRequest = { renderer.produceRequestedResources(Unit, it) }
    )
}

@WearPreviewDevices
fun ConnectingRemoteTimerLayoutPreview(context: Context): TilePreviewData {
    val state = RemoteTimerTileState(
        connectionStatus = WearConnectionStatus.CONNECTING,
        isLocalTimer = true,
        timerModel = TimerModel().apply {
            timerLengthInMins = 5
            isRunning = false
        }
    )
    val renderer = SleepTimerTileRenderer(context, debugResourceMode = true)

    return TilePreviewData(
        onTileRequest = { renderer.renderTimeline(state, it) },
        onTileResourceRequest = { renderer.produceRequestedResources(Unit, it) }
    )
}

@WearPreviewDevices
fun ProgressLocalTimerLayoutPreview(context: Context): TilePreviewData {
    val state = TimerTileState(
        isLocalTimer = true,
        timerModel = TimerModel().apply {
            timerLengthInMins = 5
            isRunning = true
            startTimer()
        }
    )
    val renderer = SleepTimerTileRenderer(context, debugResourceMode = true)

    return TilePreviewData(
        onTileRequest = { renderer.renderTimeline(state, it) },
        onTileResourceRequest = { renderer.produceRequestedResources(Unit, it) }
    )
}

@WearPreviewDevices
fun ProgressRemoteTimerLayoutPreview(context: Context): TilePreviewData {
    val state = TimerTileState(
        isLocalTimer = false,
        timerModel = TimerModel().apply {
            timerLengthInMins = 65
            isRunning = true
            startTimer()
        }
    )
    val renderer = SleepTimerTileRenderer(context, debugResourceMode = true)

    return TilePreviewData(
        onTileRequest = { renderer.renderTimeline(state, it) },
        onTileResourceRequest = { renderer.produceRequestedResources(Unit, it) }
    )
}