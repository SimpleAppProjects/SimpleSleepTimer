package com.thewizrd.simplesleeptimer

import androidx.lifecycle.ViewModel

class SleepTimerViewModel : ViewModel() {
    companion object {
        const val DEFAULT_TIME_MIN = 5
    }

    var progressTimeInMins: Int = DEFAULT_TIME_MIN

    override fun onCleared() {
        super.onCleared()
        progressTimeInMins = DEFAULT_TIME_MIN
    }
}