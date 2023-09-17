package com.thewizrd.simplesleeptimer.wearable.tiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.tools.TileLayoutPreview
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.sleeptimer.TimerModel

@OptIn(ExperimentalHorologistApi::class)
@WearPreviewDevices
@Composable
fun StartLocalTimerLayoutPreview() {
    val context = LocalContext.current
    val state = remember {
        TimerTileState(
            isLocalTimer = true,
            timerModel = TimerModel().apply {
                timerLengthInMins = 5
                isRunning = false
            }
        )
    }
    val renderer = remember {
        SleepTimerTileRenderer(context, debugResourceMode = true)
    }

    TileLayoutPreview(
        state = state,
        resourceState = Unit,
        renderer = renderer
    )
}

@OptIn(ExperimentalHorologistApi::class)
@WearPreviewDevices
@Composable
fun DisconnectedRemoteTimerLayoutPreview() {
    val context = LocalContext.current
    val state = remember {
        RemoteTimerTileState(
            connectionStatus = WearConnectionStatus.DISCONNECTED,
            isLocalTimer = true,
            timerModel = TimerModel().apply {
                timerLengthInMins = 5
                isRunning = false
            }
        )
    }
    val renderer = remember {
        SleepTimerTileRenderer(context, debugResourceMode = true)
    }

    TileLayoutPreview(
        state = state,
        resourceState = Unit,
        renderer = renderer
    )
}

@OptIn(ExperimentalHorologistApi::class)
@WearPreviewDevices
@Composable
fun ConnectingRemoteTimerLayoutPreview() {
    val context = LocalContext.current
    val state = remember {
        RemoteTimerTileState(
            connectionStatus = WearConnectionStatus.CONNECTING,
            isLocalTimer = true,
            timerModel = TimerModel().apply {
                timerLengthInMins = 5
                isRunning = false
            }
        )
    }
    val renderer = remember {
        SleepTimerTileRenderer(context, debugResourceMode = true)
    }

    TileLayoutPreview(
        state = state,
        resourceState = Unit,
        renderer = renderer
    )
}

@OptIn(ExperimentalHorologistApi::class)
@WearPreviewDevices
@Composable
fun ProgressLocalTimerLayoutPreview() {
    val context = LocalContext.current
    val state = remember {
        TimerTileState(
            isLocalTimer = true,
            timerModel = TimerModel().apply {
                timerLengthInMins = 5
                isRunning = true
                startTimer()
            }
        )
    }
    val renderer = remember {
        SleepTimerTileRenderer(context, debugResourceMode = true)
    }

    TileLayoutPreview(
        state = state,
        resourceState = Unit,
        renderer = renderer
    )
}

@OptIn(ExperimentalHorologistApi::class)
@WearPreviewDevices
@Composable
fun ProgressRemoteTimerLayoutPreview() {
    val context = LocalContext.current
    val state = remember {
        TimerTileState(
            isLocalTimer = false,
            timerModel = TimerModel().apply {
                timerLengthInMins = 65
                isRunning = true
                startTimer()
            }
        )
    }
    val renderer = remember {
        SleepTimerTileRenderer(context, debugResourceMode = true)
    }

    TileLayoutPreview(
        state = state,
        resourceState = Unit,
        renderer = renderer
    )
}