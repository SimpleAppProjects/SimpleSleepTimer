package com.thewizrd.shared_resources.controls

import android.animation.LayoutTransition
import android.content.Context
import android.text.format.DateUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.RestrictTo
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import com.thewizrd.shared_resources.databinding.ViewTimerProgressBinding
import com.thewizrd.shared_resources.sleeptimer.TimerModel

class TimerProgressView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    private var binding = ViewTimerProgressBinding.inflate(LayoutInflater.from(context), this)
    private var mHideExtendButtons: Boolean = false

    private var onClick1MinListener: View.OnClickListener? = null
    private var onClick5MinListener: View.OnClickListener? = null

    override fun onFinishInflate() {
        super.onFinishInflate()

        isTransitionGroup = true
        layoutTransition = LayoutTransition()

        // Disable interaction with progress bar
        binding.timerProgressBar.isTouchEnabled = false
        binding.timerProgressBar.shouldSaveColorState = false

        binding.plus1minbtn.setOnClickListener {
            onClick1MinListener?.onClick(it)
        }
        binding.plus5minbtn.setOnClickListener {
            onClick5MinListener?.onClick(it)
        }
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

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    fun setButtonFlowBottomMargin(margin: Int) {
        binding.buttonflow.updateLayoutParams<MarginLayoutParams> {
            bottomMargin = margin
        }
    }

    fun setOnClickExtend1MinButtonListener(listener: OnClickListener?) {
        onClick1MinListener = listener
    }

    fun setOnClickExtend5MinButtonListener(listener: OnClickListener?) {
        onClick5MinListener = listener
    }

    fun showExtendButtons() {
        mHideExtendButtons = false

        binding.plus1minbtn.visibility = View.VISIBLE
        binding.plus5minbtn.visibility = View.VISIBLE
    }

    fun hideExtendButtons() {
        mHideExtendButtons = true

        binding.plus1minbtn.visibility = View.GONE
        binding.plus5minbtn.visibility = View.GONE
    }

    fun updateTimer(model: TimerModel) {
        setProgressText(model.remainingTimeInMs + DateUtils.SECOND_IN_MILLIS)
        binding.timerProgressBar.max = model.timerLengthInMs.toInt()
        binding.timerProgressBar.progress =
            (model.timerLengthInMs - model.remainingTimeInMs).toInt()

        if (!mHideExtendButtons) {
            val remainingMinsMs =
                model.remainingTimeInMs - (model.remainingTimeInMs % DateUtils.MINUTE_IN_MILLIS)
            val show1Min =
                remainingMinsMs < (TimerModel.MAX_TIME_IN_MINS - 1).times(DateUtils.MINUTE_IN_MILLIS)
            val show5min =
                remainingMinsMs < (TimerModel.MAX_TIME_IN_MINS - 5).times(DateUtils.MINUTE_IN_MILLIS)

            if (show1Min) {
                binding.plus1minbtn.visibility = View.VISIBLE
            } else {
                binding.plus1minbtn.visibility = View.GONE
            }
            if (show5min) {
                binding.plus5minbtn.visibility = View.VISIBLE
            } else {
                binding.plus5minbtn.visibility = View.GONE
            }
        }
    }
}