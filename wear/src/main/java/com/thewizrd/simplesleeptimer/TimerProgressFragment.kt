package com.thewizrd.simplesleeptimer

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.simplesleeptimer.databinding.FragmentSleeptimerStopBinding
import java.util.*

class TimerProgressFragment : Fragment() {
    private lateinit var binding: FragmentSleeptimerStopBinding

    private val timerModel: TimerModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSleeptimerStopBinding.inflate(inflater, container, false)

        binding.timerProgressScroller.isTouchEnabled = false
        binding.timerProgressScroller.shouldSaveColorState = false

        binding.fab.setOnClickListener {
            LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(Intent(SleepTimerHelper.SleepTimerStopPath))
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        startUpdatingTime()
    }

    override fun onStop() {
        stopUpdatingTime()
        super.onStop()
    }

    private fun setProgressText(progressMs: Long) {
        val hours = progressMs / 3600000L
        val mins = progressMs % 3600000L / 60000L
        val secs = progressMs / 1000 % 60

        when {
            hours > 0 -> {
                binding.progressText.text =
                    String.format(Locale.ROOT, "%02d:%02d:%02d", hours, mins, secs)
            }
            mins > 0 -> {
                binding.progressText.text = String.format(Locale.ROOT, "%02d:%02d", mins, secs)
            }
            else -> {
                binding.progressText.text = String.format(Locale.ROOT, "%02d", secs)
            }
        }
    }

    private fun startUpdatingTime() {
        stopUpdatingTime()
        binding.root.post(updateRunnable)
    }

    private fun stopUpdatingTime() {
        binding.root.removeCallbacks(updateRunnable)
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            val startTime = SystemClock.elapsedRealtime()
            // If no timers require continuous updates, avoid scheduling the next update.
            if (!timerModel.isRunning) {
                return
            } else {
                setProgressText(timerModel.remainingTimeInMs + DateUtils.SECOND_IN_MILLIS)
                binding.timerProgressScroller.max = timerModel.timerLengthInMs.toInt()
                binding.timerProgressScroller.progress =
                    (timerModel.timerLengthInMs - timerModel.remainingTimeInMs).toInt()
            }
            val endTime = SystemClock.elapsedRealtime()

            binding.root.postOnAnimationDelayed(this, startTime + 20 - endTime)
        }
    }
}