package com.thewizrd.shared_resources.sleeptimer

import android.text.format.DateUtils
import androidx.annotation.RestrictTo

class TimerDataModel private constructor() {
    companion object {
        private val sDataModel = TimerDataModel()

        fun getDataModel(): TimerDataModel {
            return sDataModel
        }
    }

    var isRunning = false
    var startTimeInMs: Long = 0
    var endTimeInMs: Long = 0
    var timerLengthInMs: Long = 0

    val remainingTimeInMs: Long
        get() {
            return this.endTimeInMs - System.currentTimeMillis()
        }

    fun startTimer(startTimeInMins: Int) {
        isRunning = true
        this.timerLengthInMs = (startTimeInMins * DateUtils.MINUTE_IN_MILLIS)
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
        isRunning = false
        startTimeInMs = 0
        endTimeInMs = 0
        timerLengthInMs = 0
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    fun updateModel(model: TimerModel) {
        this.isRunning = model.isRunning
        this.startTimeInMs = model.startTimeInMs
        this.endTimeInMs = model.endTimeInMs
        this.timerLengthInMs = model.timerLengthInMs
    }

    fun toModel(): TimerModel {
        return TimerModel().also { m ->
            m.isRunning = this.isRunning
            m.startTimeInMs = this.startTimeInMs
            m.endTimeInMs = this.endTimeInMs
            m.timerLengthInMs = this.timerLengthInMs
        }
    }
}