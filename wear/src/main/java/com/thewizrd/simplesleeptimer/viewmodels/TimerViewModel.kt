package com.thewizrd.simplesleeptimer.viewmodels

import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.simplesleeptimer.preferences.Settings
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

open class TimerViewModel : ViewModel() {
    companion object {
        private val MAX_TIME_IN_MINS = TimeUnit.HOURS.toMinutes(24)
        private val MAX_TIME_IN_MILLIS = TimeUnit.MINUTES.toMillis(MAX_TIME_IN_MINS)
        private val ONE_MIN_IN_MILLIS = TimeUnit.MINUTES.toMillis(1)
    }

    private val viewModelState = MutableStateFlow(TimerUiState())
    private val timerEventFlow = MutableSharedFlow<TimerOperation>(
        replay = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val uiState = viewModelState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        viewModelState.value
    )

    val timerEvents = timerEventFlow.shareIn(
        viewModelScope,
        SharingStarted.Lazily,
        0
    )

    fun updateTimerState(
        isRunning: Boolean? = null,
        timerLengthInMs: Long? = null,
        remainingTimeInMs: Long? = null,
        isLocalTimer: Boolean? = null,
        isLoading: Boolean? = null,
    ) {
        viewModelState.update {
            it.copy(
                isRunning = isRunning ?: it.isRunning,
                timerLengthInMs = timerLengthInMs ?: it.timerLengthInMs,
                remainingTimeInMs = remainingTimeInMs ?: it.remainingTimeInMs,
                isLocalTimer = isLocalTimer ?: it.isLocalTimer,
                isLoading = isLoading ?: it.isLoading,
            )
        }
    }

    fun updateTimerState(timerModel: TimerModel) {
        viewModelState.update {
            it.copy(
                isRunning = timerModel.isRunning,
                timerLengthInMs = timerModel.timerLengthInMs,
                remainingTimeInMs = timerModel.remainingTimeInMs
            )
        }
    }

    fun requestTimerOp(op: TimerOperation) {
        viewModelScope.launch {
            timerEventFlow.emit(op)
        }

        when (op) {
            TimerOperation.ADD_1M -> {
                viewModelState.update {
                    it.copy(
                        timerLengthInMs = MAX_TIME_IN_MILLIS
                            .coerceAtMost(it.timerLengthInMs + ONE_MIN_IN_MILLIS)
                    )
                }
            }

            TimerOperation.ADD_5M -> {
                viewModelState.update {
                    it.copy(
                        timerLengthInMs = MAX_TIME_IN_MILLIS
                            .coerceAtMost(
                                it.timerLengthInMs + ONE_MIN_IN_MILLIS.times(5)
                            )
                    )
                }
            }

            TimerOperation.MINUS_1M -> {
                viewModelState.update {
                    it.copy(
                        timerLengthInMs = (it.timerLengthInMs - ONE_MIN_IN_MILLIS)
                            .coerceAtLeast(0)
                    )
                }
            }

            TimerOperation.MINUS_5M -> {
                viewModelState.update {
                    it.copy(
                        timerLengthInMs = (it.timerLengthInMs - ONE_MIN_IN_MILLIS.times(5))
                            .coerceAtLeast(0)
                    )
                }
            }

            TimerOperation.STOP -> {
                // Reset timer length
                viewModelState.update {
                    it.copy(timerLengthInMs = Settings.getLastTimeSet() * ONE_MIN_IN_MILLIS)
                }
            }

            else -> {
                // ignore
            }
        }
    }
}

data class TimerUiState(
    val isRunning: Boolean = false,
    val timerLengthInMs: Long = TimerModel.DEFAULT_TIME_MIN * DateUtils.MINUTE_IN_MILLIS,
    val remainingTimeInMs: Long = 0,
    val isLocalTimer: Boolean = true,
    val isLoading: Boolean = false
) {
    fun getTimerProgress(): Float = 1f - (remainingTimeInMs.toFloat() / timerLengthInMs)

    fun getTimerLengthInMins(): Int = TimeUnit.MILLISECONDS.toMinutes(timerLengthInMs).toInt()
}

enum class TimerOperation {
    START,
    STOP,
    EXTEND_1M,
    EXTEND_5M,
    ADD_1M,
    ADD_5M,
    MINUS_1M,
    MINUS_5M
}