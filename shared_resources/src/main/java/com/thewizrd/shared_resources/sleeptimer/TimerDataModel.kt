package com.thewizrd.shared_resources.sleeptimer

import android.text.format.DateUtils

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

    fun stopTimer() {
        isRunning = false
        startTimeInMs = 0
        endTimeInMs = 0
        timerLengthInMs = 0
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