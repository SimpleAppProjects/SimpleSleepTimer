package com.thewizrd.shared_resources.controls

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.annotation.RestrictTo
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.InputDeviceCompat
import androidx.core.view.MotionEventCompat
import androidx.core.view.ViewConfigurationCompat
import androidx.core.view.updateLayoutParams
import com.devadvance.circularseekbar.CircularSeekBar
import com.thewizrd.shared_resources.R
import com.thewizrd.shared_resources.databinding.ViewTimerStartBinding
import com.thewizrd.shared_resources.utils.TimerStringFormatter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

class TimerStartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    private var binding = ViewTimerStartBinding.inflate(LayoutInflater.from(context), this)
    private var onProgressChangedListener: OnProgressChangedListener? = null

    override fun onFinishInflate() {
        super.onFinishInflate()

        isTransitionGroup = true
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

        binding.timerProgressScroller.shouldSaveColorState = false
        binding.timerProgressScroller.setOnSeekBarChangeListener(object :
            CircularSeekBar.OnCircularSeekBarChangeListener {
            override fun onProgressChanged(
                circularSeekBar: CircularSeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                setProgressText(progress)
                onProgressChangedListener?.onProgressChanged(progress, fromUser)
            }

            override fun onStartTrackingTouch(seekBar: CircularSeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: CircularSeekBar?) {
            }
        })
        binding.timerProgressScroller.setOnGenericMotionListener { v, ev ->
            if (ev.action == MotionEvent.ACTION_SCROLL &&
                ev.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)
            ) {
                // Don't forget the negation here
                val delta = -ev.getAxisValue(MotionEventCompat.AXIS_SCROLL) *
                        ViewConfigurationCompat.getScaledVerticalScrollFactor(
                            ViewConfiguration.get(context), context
                        )

                // Swap these axes to scroll horizontally instead
                val sign = delta.sign
                if (sign > 0) {
                    binding.timerProgressScroller.progress = min(
                        binding.timerProgressScroller.progress + 1,
                        binding.timerProgressScroller.max
                    )
                } else if (sign < 0) {
                    binding.timerProgressScroller.progress = max(
                        binding.timerProgressScroller.progress - 1, 0
                    )
                }

                true
            } else {
                false
            }
        }

        binding.minus5minbtn.setOnClickListener {
            binding.timerProgressScroller.progress =
                (binding.timerProgressScroller.progress - 5).coerceAtLeast(0)
        }
        binding.minus1minbtn.setOnClickListener {
            binding.timerProgressScroller.progress =
                (binding.timerProgressScroller.progress - 1).coerceAtLeast(0)
        }
        binding.plus1minbtn.setOnClickListener {
            binding.timerProgressScroller.progress =
                binding.timerProgressScroller.max.coerceAtMost(binding.timerProgressScroller.progress + 1)
        }
        binding.plus5minbtn.setOnClickListener {
            binding.timerProgressScroller.progress =
                binding.timerProgressScroller.max.coerceAtMost(binding.timerProgressScroller.progress + 5)
        }

        binding.timerProgressScroller.requestFocus()
    }

    fun setOnProgressChangedListener(listener: OnProgressChangedListener?) {
        this.onProgressChangedListener = listener
    }

    fun setTimerMax(timeInMins: Int) {
        binding.timerProgressScroller.max = timeInMins
    }

    fun getTimerProgress(): Int {
        return binding.timerProgressScroller.progress
    }

    fun setTimerProgress(timeInMins: Int) {
        binding.timerProgressScroller.progress = timeInMins
    }

    private fun setProgressText(progress: Int) {
        val hours = progress / 60
        val minutes = progress - (hours * 60)

        if (hours > 0) {
            binding.progressText.text =
                context.getString(R.string.timer_progress_hours_minutes, hours, minutes)
        } else {
            binding.progressText.text =
                TimerStringFormatter.getNumberFormattedQuantityString(
                    context, R.plurals.minutes_short, minutes
                )
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    fun setButtonFlowBottomMargin(margin: Int) {
        binding.buttonflow.updateLayoutParams<MarginLayoutParams> {
            bottomMargin = margin
        }
    }

    interface OnProgressChangedListener {
        fun onProgressChanged(progress: Int, fromUser: Boolean)
    }
}