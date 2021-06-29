package com.thewizrd.simplesleeptimer

import android.content.Intent
import android.os.Bundle
import android.support.wearable.input.RotaryEncoder
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnGenericMotionListener
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.devadvance.circularseekbar.CircularSeekBar
import com.devadvance.circularseekbar.CircularSeekBar.OnCircularSeekBarChangeListener
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.shared_resources.utils.TimerStringFormatter
import com.thewizrd.simplesleeptimer.databinding.FragmentSleeptimerStartBinding

class TimerStartFragment : Fragment() {
    private lateinit var binding: FragmentSleeptimerStartBinding
    private lateinit var backPressedCallback: OnBackPressedCallback

    private val timerModel: TimerModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        backPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (fragmentManager != null) {
                    fragmentManager!!.popBackStack(
                        "players",
                        FragmentManager.POP_BACK_STACK_INCLUSIVE
                    )
                }
                this.isEnabled = false
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSleeptimerStartBinding.inflate(inflater, container, false)

        binding.timerProgressScroller.shouldSaveColorState = false
        binding.timerProgressScroller.setOnSeekBarChangeListener(object :
            OnCircularSeekBarChangeListener {
            override fun onProgressChanged(
                circularSeekBar: CircularSeekBar,
                progress: Int,
                fromUser: Boolean
            ) {
                timerModel.timerLengthInMs = progress * DateUtils.MINUTE_IN_MILLIS
                setProgressText(progress)
                binding.fab.post {
                    if (progress >= 1) {
                        binding.fab.show()
                    } else {
                        binding.fab.hide()
                    }
                }
            }

            override fun onStopTrackingTouch(seekBar: CircularSeekBar) {}

            override fun onStartTrackingTouch(seekBar: CircularSeekBar) {}
        })
        binding.timerProgressScroller.setOnGenericMotionListener(OnGenericMotionListener { view, event ->
            if (event.action == MotionEvent.ACTION_SCROLL && RotaryEncoder.isFromRotaryEncoder(event)) {
                // Don't forget the negation here
                val delta =
                    -RotaryEncoder.getRotaryAxisValue(event) * RotaryEncoder.getScaledScrollFactor(
                        view.context
                    )

                // Swap these axes if you want to do horizontal scrolling instead
                val sign = Math.signum(delta).toInt()
                if (sign > 0) {
                    binding.timerProgressScroller.progress =
                        Math.min(
                            binding.timerProgressScroller.progress + 1,
                            binding.timerProgressScroller.max
                        )
                } else if (sign < 0) {
                    binding.timerProgressScroller.progress =
                        Math.max(binding.timerProgressScroller.progress - 1, 0)
                }
                return@OnGenericMotionListener true
            }
            false
        })
        binding.timerProgressScroller.requestFocus()

        binding.timerProgressScroller.max = TimerModel.MAX_TIME_IN_MINS
        binding.timerProgressScroller.progress = timerModel.timerLengthInMins

        binding.minus5minbtn.setOnClickListener {
            binding.timerProgressScroller.progress =
                Math.max(binding.timerProgressScroller.progress - 5, 0)
        }
        binding.minus1minbtn.setOnClickListener {
            binding.timerProgressScroller.progress =
                Math.max(binding.timerProgressScroller.progress - 1, 0)
        }
        binding.plus1minbtn.setOnClickListener {
            binding.timerProgressScroller.progress =
                Math.min(
                    binding.timerProgressScroller.progress + 1,
                    binding.timerProgressScroller.max
                )
        }
        binding.plus5minbtn.setOnClickListener {
            binding.timerProgressScroller.progress =
                Math.min(
                    binding.timerProgressScroller.progress + 5,
                    binding.timerProgressScroller.max
                )
        }

        binding.fab.setOnClickListener {
            LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(
                    Intent(SleepTimerHelper.SleepTimerStartPath)
                        .putExtra(SleepTimerHelper.EXTRA_TIME_IN_MINS, timerModel.timerLengthInMins)
                )
        }

        binding.bottomActionDrawer.setOnClickListener {
            if (fragmentManager != null) {
                requireFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, MusicPlayersFragment())
                    .hide(this@TimerStartFragment)
                    .addToBackStack("players")
                    .commit()
                backPressedCallback.isEnabled = true
            }
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        binding.timerProgressScroller.requestFocus()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            binding.timerProgressScroller.requestFocus()
        }
    }

    private fun setProgressText(progress: Int) {
        val hours = progress / 60
        val minutes = progress - hours * 60

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