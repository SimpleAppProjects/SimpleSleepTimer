package com.thewizrd.shared_resources.sleeptimer

import android.text.format.DateUtils
import androidx.lifecycle.ViewModel

class TimerModel : ViewModel() {
    companion object {
        const val DEFAULT_TIME_MIN = 5
        const val MAX_TIME_IN_MINS = 120
        private const val DEFAULT_TIME_MS = DEFAULT_TIME_MIN * DateUtils.MINUTE_IN_MILLIS
    }

    var isRunning = false
    var startTimeInMs: Long = 0
    var endTimeInMs: Long = 0
    var timerLengthInMs: Long = DEFAULT_TIME_MS
    var timerLengthInMins: Int
        get() {
            return (timerLengthInMs / DateUtils.MINUTE_IN_MILLIS).toInt()
        }
        set(value) {
            timerLengthInMs = value * DateUtils.MINUTE_IN_MILLIS
        }
    val remainingTimeInMs: Long
        get() {
            return this.endTimeInMs - System.currentTimeMillis()
        }

    fun updateModel(model: TimerModel) {
        this.isRunning = model.isRunning
        this.startTimeInMs = model.startTimeInMs
        this.endTimeInMs = model.endTimeInMs
        this.timerLengthInMs = model.timerLengthInMs
    }

    override fun onCleared() {
        super.onCleared()
        isRunning = false
        startTimeInMs = 0
        endTimeInMs = 0
        timerLengthInMs = DEFAULT_TIME_MS
    }
}