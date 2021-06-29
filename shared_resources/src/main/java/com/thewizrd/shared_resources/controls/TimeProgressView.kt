package com.thewizrd.shared_resources.controls

import android.content.Context
import android.text.format.DateUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import com.thewizrd.shared_resources.databinding.ViewTimerProgressBinding
import com.thewizrd.shared_resources.sleeptimer.TimerModel

class TimeProgressView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    private var binding = ViewTimerProgressBinding.inflate(LayoutInflater.from(context), this)

    override fun onFinishInflate() {
        super.onFinishInflate()

        isTransitionGroup = true

        // Disable interaction with progress bar
        binding.timerProgressBar.isTouchEnabled = false
        binding.timerProgressBar.shouldSaveColorState = false
    }

    private fun setProgressText(progressMs: Long) {
        val hours = progressMs / DateUtils.HOUR_IN_MILLIS
        val mins = progressMs % DateUtils.HOUR_IN_MILLIS / DateUtils.MINUTE_IN_MILLIS
        val secs = (progressMs / 1000) % 60

        when {
            hours > 0 -> {
                binding.progressText.text = String.format("%02d:%02d:%02d", hours, mins, secs)
            }
            mins > 0 -> {
                binding.progressText.text = String.format("%02d:%02d", mins, secs)
            }
            else -> {
                binding.progressText.text = String.format("%02d", secs)
            }
        }
    }

    fun updateTimer(model: TimerModel) {
        setProgressText(model.remainingTimeInMs + DateUtils.SECOND_IN_MILLIS)
        binding.timerProgressBar.max = model.timerLengthInMs.toInt()
        binding.timerProgressBar.progress =
            (model.timerLengthInMs - model.remainingTimeInMs).toInt()
    }
}