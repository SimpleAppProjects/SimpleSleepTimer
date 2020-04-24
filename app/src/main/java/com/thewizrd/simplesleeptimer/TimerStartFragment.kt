package com.thewizrd.simplesleeptimer

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.TransitionSet
import com.devadvance.circularseekbar.CircularSeekBar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.thewizrd.simplesleeptimer.databinding.FragmentTimerStartBinding

class TimerStartFragment : Fragment() {
    private lateinit var binding: FragmentTimerStartBinding
    private var fab: FloatingActionButton? = null

    private lateinit var viewModel: SleepTimerViewModel

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

        viewModel =
            ViewModelProvider(requireActivity(), ViewModelProvider.NewInstanceFactory()).get(
                SleepTimerViewModel::class.java
            )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentTimerStartBinding.inflate(inflater, container, false)
        fab = activity?.findViewById(R.id.fab)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.timerProgressScroller.setOnSeekBarChangeListener(object :
            CircularSeekBar.OnCircularSeekBarChangeListener {
            override fun onProgressChanged(
                circularSeekBar: CircularSeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                viewModel.progressTimeInMins = progress
                setProgressText(progress)
                fab?.isEnabled = progress >= 1
            }

            override fun onStartTrackingTouch(seekBar: CircularSeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: CircularSeekBar?) {
            }
        })

        binding.timerProgressScroller.max = 1440 // 24hrs
        binding.timerProgressScroller.progress = viewModel.progressTimeInMins

        fab?.setImageResource(R.drawable.ic_play_arrow)
    }

    fun setProgressText(progress: Int) {
        val hours = progress / 60
        val minutes = progress - (hours * 60)

        binding.progressText.text = this.getString(R.string.time_format, hours, minutes)
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}