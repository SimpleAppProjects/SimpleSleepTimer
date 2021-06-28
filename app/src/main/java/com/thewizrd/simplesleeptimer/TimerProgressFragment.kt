package com.thewizrd.simplesleeptimer

import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.TransitionSet
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.thewizrd.simplesleeptimer.databinding.FragmentTimerProgressBinding
import com.thewizrd.simplesleeptimer.model.TimerDataModel

class TimerProgressFragment : Fragment() {
    private lateinit var binding: FragmentTimerProgressBinding
    private var fab: FloatingActionButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Animations
        enterTransition = TransitionSet()
            .addTransition(Slide(Gravity.BOTTOM))
            .addTransition(Fade(Fade.IN))
            .setDuration(250)
        exitTransition = TransitionSet()
            .addTransition(Fade(Fade.OUT))
            .setDuration(250)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTimerProgressBinding.inflate(inflater, container, false)
        fab = requireActivity().findViewById(R.id.fab)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Disable interaction with progress bar
        binding.timerProgressBar.isTouchEnabled = false
        binding.timerProgressBar.shouldSaveColorState = false

        fab?.setImageResource(R.drawable.ic_stop)
    }

    override fun onStart() {
        super.onStart()
        startUpdatingTime()
    }

    override fun onStop() {
        stopUpdatingTime()
        super.onStop()
    }

    fun setProgressText(progressMs: Long) {
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

    override fun onDestroyView() {
        super.onDestroyView()
    }

    private fun startUpdatingTime() {
        stopUpdatingTime()
        binding.root.post(updateRunnable)
    }

    private fun stopUpdatingTime() {
        binding.root.removeCallbacks(updateRunnable)
    }

    private val updateRunnable = object : Runnable {
        private val model = TimerDataModel.getDataModel()

        override fun run() {
            val startTime = SystemClock.elapsedRealtime()
            // If no timers require continuous updates, avoid scheduling the next update.
            if (!model.isRunning) {
                return
            } else {
                setProgressText(model.remainingTimeInMs + DateUtils.SECOND_IN_MILLIS)
                binding.timerProgressBar.max = model.timerLengthInMs.toInt()
                binding.timerProgressBar.progress =
                    (model.timerLengthInMs - model.remainingTimeInMs).toInt()
            }
            val endTime = SystemClock.elapsedRealtime()

            binding.root.postOnAnimationDelayed(this, startTime + 20 - endTime)
        }
    }
}