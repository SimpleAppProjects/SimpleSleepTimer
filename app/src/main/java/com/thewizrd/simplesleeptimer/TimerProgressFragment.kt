package com.thewizrd.simplesleeptimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.util.ObjectsCompat
import androidx.fragment.app.Fragment
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.TransitionSet
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.thewizrd.simplesleeptimer.databinding.FragmentTimerProgressBinding
import com.thewizrd.simplesleeptimer.services.TimerService

class TimerProgressFragment : Fragment() {
    private lateinit var binding: FragmentTimerProgressBinding
    private var fab: FloatingActionButton? = null

    private lateinit var mBroadcastReceiver: BroadcastReceiver

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
    ): View? {
        binding = FragmentTimerProgressBinding.inflate(inflater, container, false)
        fab = requireActivity().findViewById(R.id.fab)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Disable interaction with progress bar
        binding.timerProgressBar.isTouchEnabled = false

        fab?.setImageResource(R.drawable.ic_pause)
    }

    override fun onResume() {
        super.onResume()
        mBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (ObjectsCompat.equals(intent?.action, TimerService.ACTION_TIME_UPDATED)) {
                    val progressMs = intent?.getLongExtra(TimerService.EXTRA_TIME_IN_MS, 0) ?: 0
                    val startTimeMs =
                        intent?.getLongExtra(TimerService.EXTRA_START_TIME_IN_MS, 0) ?: 0

                    binding.timerProgressBar.max = startTimeMs.toInt()
                    binding.timerProgressBar.progress = (startTimeMs - progressMs).toInt()
                    setProgressText(progressMs)
                }
            }
        }
        requireContext().registerReceiver(
            mBroadcastReceiver,
            IntentFilter(TimerService.ACTION_TIME_UPDATED)
        )
    }

    override fun onPause() {
        requireContext().unregisterReceiver(mBroadcastReceiver)
        super.onPause()
    }

    fun setProgressText(progressMs: Long) {
        val hours = progressMs / 3600000L
        val mins = progressMs % 3600000L / 60000L
        val secs = (progressMs / 1000) % 60

        if (hours > 0) {
            binding.progressText.text = String.format("%02d:%02d:%02d", hours, mins, secs)
        } else if (mins > 0) {
            binding.progressText.text = String.format("%02d:%02d", mins, secs)
        } else {
            binding.progressText.text = String.format("%02d", secs)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}