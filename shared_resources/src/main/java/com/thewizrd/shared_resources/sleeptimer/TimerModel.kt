package com.thewizrd.shared_resources.sleeptimer

import android.text.format.DateUtils
import androidx.lifecycle.ViewModel

class TimerModel : ViewModel() {
    companion object {
        const val DEFAULT_TIME_MIN = 15
        const val MAX_TIME_IN_MINS = 1440 // 24hrs
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

    fun startTimer() {
        this.isRunning = true
        this.startTimeInMs = System.currentTimeMillis()
        this.endTimeInMs = this.startTimeInMs + this.timerLengthInMs
    }

    fun extend1Min() {
        val currentTimeMs = System.currentTimeMillis()
        val extendTimeMs = (1 * DateUtils.MINUTE_IN_MILLIS)
        val newTimerLengthInMs = this.timerLengthInMs + extendTimeMs
        val newEndTimeMs = this.endTimeInMs + extendTimeMs
        if ((newEndTimeMs - currentTimeMs) <= TimerModel.MAX_TIME_IN_MINS * DateUtils.MINUTE_IN_MILLIS) {
            this.timerLengthInMs = newTimerLengthInMs
            this.endTimeInMs += extendTimeMs
        }
    }

    fun extend5Min() {
        val currentTimeMs = System.currentTimeMillis()
        val extendTimeMs = (5 * DateUtils.MINUTE_IN_MILLIS)
        val newTimerLengthInMs = this.timerLengthInMs + extendTimeMs
        val newEndTimeMs = this.endTimeInMs + extendTimeMs
        if ((newEndTimeMs - currentTimeMs) <= TimerModel.MAX_TIME_IN_MINS * DateUtils.MINUTE_IN_MILLIS) {
            this.timerLengthInMs = newTimerLengthInMs
            this.endTimeInMs += extendTimeMs
        }
    }

    fun stopTimer() {
        this.isRunning = false
        timerLengthInMs = DEFAULT_TIME_MS
    }

    fun updateModel(model: TimerModel) {
        this.isRunning = model.isRunning
        this.startTimeInMs = model.startTimeInMs
        this.endTimeInMs = model.endTimeInMs
        this.timerLengthInMs = model.timerLengthInMs
    }

    fun clearModel() {
        isRunning = false
        startTimeInMs = 0
        endTimeInMs = 0
        timerLengthInMs = DEFAULT_TIME_MS
    }

    override fun onCleared() {
        super.onCleared()
        clearModel()
    }
}