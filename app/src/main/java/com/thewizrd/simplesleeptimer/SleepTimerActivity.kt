package com.thewizrd.simplesleeptimer

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.transition.TransitionManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.transition.MaterialFadeThrough
import com.thewizrd.shared_resources.controls.TimerStartView
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.simplesleeptimer.databinding.ActivityMainBinding
import com.thewizrd.simplesleeptimer.model.TimerDataModel
import com.thewizrd.simplesleeptimer.services.TimerService
import com.thewizrd.simplesleeptimer.utils.ActivityUtils

class SleepTimerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var mBottomSheetBehavior: BottomSheetBehavior<View>

    private val timerModel: TimerModel by viewModels()

    private lateinit var mTimerBinder: TimerService.LocalBinder
    private var mBound: Boolean = false
    private lateinit var mBroadcastReceiver: BroadcastReceiver

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            mTimerBinder = service as TimerService.LocalBinder
            mBound = true

            if (mTimerBinder.isRunning()) {
                showTimerProgressView()
            } else {
                showTimerStartView()
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)

        // Fix statusbar
        ActivityUtils.setStatusBarColor(
            window,
            ContextCompat.getColor(this, R.color.colorSurface),
            true
        )

        val musicPlayersFragment =
            supportFragmentManager.findFragmentById(R.id.musicplayer_fragment) as? MusicPlayersFragment
        mBottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        mBottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                musicPlayersFragment?.onSlide(bottomSheet, slideOffset)
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                musicPlayersFragment?.onStateChanged(bottomSheet, newState)
            }
        })
        musicPlayersFragment?.onBottomSheetBehaviorInitialized(binding.bottomSheet)
        mBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        binding.bottomSheet.setOnClickListener {
            mBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomSheet) { v, insets ->
            mBottomSheetBehavior.expandedOffset = insets.systemWindowInsetTop
            insets
        }

        binding.fab.setOnClickListener {
            if (mBound) {
                var toRun = false
                if (mTimerBinder.isRunning()) {
                    mTimerBinder.cancelTimer()
                    toRun = false
                } else {
                    applicationContext.startService(
                        Intent(applicationContext, TimerService::class.java)
                            .setAction(TimerService.ACTION_START_TIMER)
                            .putExtra(TimerService.EXTRA_TIME_IN_MINS, timerModel.timerLengthInMins)
                    )
                    toRun = true
                }

                animateToView(toRun)
            }
        }

        binding.timerStartView.setOnProgressChangedListener(object :
            TimerStartView.OnProgressChangedListener {
            override fun onProgressChanged(progress: Int, fromUser: Boolean) {
                timerModel.timerLengthInMins = progress
                if (progress >= 1) {
                    binding.fab.post { binding.fab.show() }
                } else {
                    binding.fab.post { binding.fab.hide() }
                }
            }
        })

        binding.timerStartView.setTimerMax(TimerModel.MAX_TIME_IN_MINS)
        binding.timerStartView.setTimerProgress(timerModel.timerLengthInMins)
    }

    override fun onStart() {
        super.onStart()
        Intent(this, TimerService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        if (TimerDataModel.getDataModel().isRunning) {
            showTimerProgressView()
        } else {
            showTimerStartView()
        }
    }

    override fun onResume() {
        super.onResume()
        mBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                when (intent?.action) {
                    TimerService.ACTION_START_TIMER -> {
                        showTimerProgressView()
                    }
                    TimerService.ACTION_CANCEL_TIMER -> {
                        showTimerStartView()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(TimerService.ACTION_START_TIMER)
            addAction(TimerService.ACTION_CANCEL_TIMER)
        }
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(mBroadcastReceiver, filter)
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(mBroadcastReceiver)
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        stopUpdatingTime()
        unbindService(connection)
        mBound = false
    }

    override fun onBackPressed() {
        if (mBottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN &&
            mBottomSheetBehavior.state != BottomSheetBehavior.STATE_COLLAPSED
        ) {
            mBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            return
        }
        super.onBackPressed()
    }

    /* Views */
    private fun showTimerStartView() {
        stopUpdatingTime()

        binding.timerProgressView.visibility = View.GONE
        binding.timerStartView.visibility = View.VISIBLE
        collapseBottomSheet()

        updateFab()
    }

    private fun showTimerProgressView() {
        binding.timerProgressView.visibility = View.VISIBLE
        binding.timerStartView.visibility = View.GONE
        hideBottomSheet()

        updateFab()

        startUpdatingTime()
    }

    private fun updateFab() {
        if (TimerDataModel.getDataModel().isRunning) {
            binding.fab.setImageResource(R.drawable.ic_stop)
        } else {
            binding.fab.setImageResource(R.drawable.ic_play_arrow)
        }
    }

    private fun collapseBottomSheet() {
        if (mBottomSheetBehavior.state != BottomSheetBehavior.STATE_COLLAPSED) {
            mBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            mBottomSheetBehavior.isHideable = false
            binding.shadow.visibility = View.VISIBLE
        }
    }

    private fun hideBottomSheet() {
        if (mBottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
            mBottomSheetBehavior.isHideable = true
            mBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            binding.shadow.visibility = View.INVISIBLE
        }
    }

    private fun animateToView(isRunning: Boolean) {
        // Set up a new MaterialSharedAxis in the specified axis and direction.
        val transform = MaterialFadeThrough().apply {
            duration = resources.getInteger(android.R.integer.config_longAnimTime).toLong()
        }

        // Set BottomSheet state before transitioning to avoid weird transition
        if (isRunning) {
            hideBottomSheet()
        } else {
            collapseBottomSheet()
        }

        // Begin watching for changes in the View hierarchy.
        TransitionManager.beginDelayedTransition(binding.fragmentContainer, transform)
        if (isRunning) {
            showTimerProgressView()
        } else {
            showTimerStartView()
        }
    }

    private fun startUpdatingTime() {
        stopUpdatingTime()
        binding.fragmentContainer.post(updateRunnable)
    }

    private fun stopUpdatingTime() {
        binding.fragmentContainer.removeCallbacks(updateRunnable)
    }

    private val updateRunnable = object : Runnable {
        private val model = TimerDataModel.getDataModel()

        override fun run() {
            val startTime = SystemClock.elapsedRealtime()
            // If no timers require continuous updates, avoid scheduling the next update.
            if (!model.isRunning) {
                return
            } else {
                binding.timerProgressView.updateTimer(model.toModel())
            }
            val endTime = SystemClock.elapsedRealtime()

            binding.fragmentContainer.postOnAnimationDelayed(this, startTime + 20 - endTime)
        }
    }
}