package com.thewizrd.simplesleeptimer.datastore

import com.thewizrd.shared_resources.sleeptimer.TimerModel

data class RemoteTimerStateCache(
    val isLocalTimer: Boolean = false,
    val timerModel: TimerModel? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RemoteTimerStateCache) return false

        if (isLocalTimer != other.isLocalTimer) return false
        if (timerModel != other.timerModel) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isLocalTimer.hashCode()
        result = 31 * result + (timerModel?.hashCode() ?: 0)
        return result
    }
}