package com.thewizrd.simplesleeptimer.wearable.tiles

import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.sleeptimer.TimerModel

interface TimerState {
    val isLocalTimer: Boolean
    val timerModel: TimerModel
}

data class TimerTileState(
    override val isLocalTimer: Boolean,
    override val timerModel: TimerModel
) : TimerState

data class RemoteTimerTileState(
    val connectionStatus: WearConnectionStatus,
    override val isLocalTimer: Boolean,
    override val timerModel: TimerModel,
) : TimerState