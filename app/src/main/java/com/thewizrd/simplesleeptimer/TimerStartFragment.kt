package com.thewizrd.simplesleeptimer

import android.os.Bundle
import android.text.format.DateUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.TransitionSet
import com.devadvance.circularseekbar.CircularSeekBar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.shared_resources.utils.TimerStringFormatter
import com.thewizrd.simplesleeptimer.databinding.FragmentTimerStartBinding

class TimerStartFragment : Fragment() {
    private lateinit var binding: FragmentTimerStartBinding
    private var fab: FloatingActionButton? = null

    private val timerModel: TimerModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Animations
        enterTransition = TransitionSet()
            .addTransition(Slide(Gravity.BOTTOM))
            .addTransition(Fade(Fade.IN))
            .setDuration(250)
        exitTransition = TransitionSet()
            .addTransition(Slide(Gravity.TOP))
            .addTransition(Fade(Fade.OUT))
            .setDuration(250)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTimerStartBinding.inflate(inflater, container, false)
        fab = activity?.findViewById(R.id.fab)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.timerProgressScroller.shouldSaveColorState = false
        binding.timerProgressScroller.setOnSeekBarChangeListener(object :
            CircularSeekBar.OnCircularSeekBarChangeListener {
            override fun onProgressChanged(
                circularSeekBar: CircularSeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                timerModel.timerLengthInMs = progress * DateUtils.MINUTE_IN_MILLIS
                setProgressText(progress)
                if (progress >= 1) {
                    fab?.post { fab?.show() }
                } else {
                    fab?.post { fab?.hide() }
                }
            }

            override fun onStartTrackingTouch(seekBar: CircularSeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: CircularSeekBar?) {
            }
        })

        binding.timerProgressScroller.max = TimerModel.MAX_TIME_IN_MINS
        binding.timerProgressScroller.progress = timerModel.timerLengthInMins

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

        fab?.setImageResource(R.drawable.ic_play_arrow)
    }

    fun setProgressText(progress: Int) {
        val hours = progress / 60
        val minutes = progress - (hours * 60)

        if (hours > 0) {
            binding.progressText.text =
                getString(R.string.timer_progress_hours_minutes, hours, minutes)
        } else {
            binding.progressText.text =
                TimerStringFormatter.getNumberFormattedQuantityString(
                    requireContext(), R.plurals.minutes_short, minutes
                )
        }
    }
}