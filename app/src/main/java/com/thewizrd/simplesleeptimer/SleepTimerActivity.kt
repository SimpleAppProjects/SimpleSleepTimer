package com.thewizrd.simplesleeptimer

import android.animation.*
import android.annotation.SuppressLint
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.animation.AnimationUtils
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.thewizrd.shared_resources.controls.TimerStartView
import com.thewizrd.shared_resources.services.BaseTimerService
import com.thewizrd.shared_resources.sleeptimer.TimerDataModel
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.simplesleeptimer.databinding.ActivityMainBinding
import com.thewizrd.simplesleeptimer.services.TimerService
import com.thewizrd.simplesleeptimer.utils.ActivityUtils

class SleepTimerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var mBottomSheetBehavior: BottomSheetBehavior<View>

    private val timerModel: TimerModel by viewModels()

    private lateinit var mTimerBinder: BaseTimerService.LocalBinder
    private var mBound: Boolean = false
    private lateinit var mBroadcastReceiver: BroadcastReceiver

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            mTimerBinder = service as BaseTimerService.LocalBinder
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
                            .setAction(BaseTimerService.ACTION_START_TIMER)
                            .putExtra(
                                BaseTimerService.EXTRA_TIME_IN_MINS,
                                timerModel.timerLengthInMins
                            )
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

        binding.timerProgressView.setOnClickExtend1MinButtonListener {
            if (mBound) {
                mTimerBinder.extend1MinTimer()
            }
        }
        binding.timerProgressView.setOnClickExtend5MinButtonListener {
            if (mBound) {
                mTimerBinder.extend5MinTimer()
            }
        }
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
                    BaseTimerService.ACTION_START_TIMER -> {
                        showTimerProgressView()
                    }
                    BaseTimerService.ACTION_CANCEL_TIMER -> {
                        showTimerStartView()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BaseTimerService.ACTION_START_TIMER)
            addAction(BaseTimerService.ACTION_CANCEL_TIMER)
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
        // Set BottomSheet state before transitioning to avoid weird transition
        if (isRunning) {
            hideBottomSheet()
        } else {
            collapseBottomSheet()
        }

        if (isRunning && binding.timerProgressView.visibility == View.VISIBLE ||
            !isRunning && binding.timerStartView.visibility == View.VISIBLE
        ) {
            return
        }

        val currentView = if (!isRunning) {
            binding.timerProgressView
        } else {
            binding.timerStartView
        }
        val toView = if (isRunning) {
            binding.timerProgressView
        } else {
            binding.timerStartView
        }
        toView.visibility = View.VISIBLE

        val animDuration = resources.getInteger(android.R.integer.config_longAnimTime).toLong()

        val viewTreeObserver = toView.viewTreeObserver
        viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (viewTreeObserver.isAlive) {
                    viewTreeObserver.removeOnPreDrawListener(this)
                }

                val distanceY =
                    toView.context.resources.getDimensionPixelSize(R.dimen.mtrl_transition_shared_axis_slide_distance)
                        .toFloat()
                val translationDistance = if (!isRunning) distanceY else -distanceY

                toView.translationY = -translationDistance
                currentView.translationY = 0f
                toView.alpha = 0f
                currentView.alpha = 1f

                val translateCurrent = ObjectAnimator.ofFloat(
                    currentView,
                    View.TRANSLATION_Y, translationDistance
                )
                val translateNew = ObjectAnimator.ofFloat(toView, View.TRANSLATION_Y, 0f)
                val translationAnimatorSet = AnimatorSet().apply {
                    playTogether(translateCurrent, translateNew)
                    duration = animDuration
                    interpolator = FastOutSlowInInterpolator()
                }

                val fadeOutAnimator = ObjectAnimator.ofFloat(currentView, View.ALPHA, 0f)
                fadeOutAnimator.duration = animDuration / 2
                fadeOutAnimator.addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
                    val view = currentView
                    val startValue = 1f
                    val endValue = 0f
                    val startFraction = 0f
                    val endFraction = 0.35f

                    @SuppressLint("RestrictedApi")
                    override fun onAnimationUpdate(animation: ValueAnimator) {
                        val progress = animation.animatedValue as Float
                        view.alpha = AnimationUtils.lerp(
                            startValue,
                            endValue,
                            startFraction,
                            endFraction,
                            progress
                        )
                    }
                })
                fadeOutAnimator.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        super.onAnimationStart(animation)
                    }

                    override fun onAnimationEnd(animation: Animator?) {
                        super.onAnimationEnd(animation)
                        if (isRunning) {
                            showTimerProgressView()
                        } else {
                            showTimerStartView()
                        }
                    }
                })

                val fadeInAnimator = ObjectAnimator.ofFloat(toView, View.ALPHA, 1f).apply {
                    duration = animDuration / 2
                    //startDelay = animDuration / 2
                }
                fadeInAnimator.addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
                    val view = toView
                    val startValue = 0f
                    val endValue = 1f
                    val startFraction = 0f
                    val endFraction = 0.35f

                    @SuppressLint("RestrictedApi")
                    override fun onAnimationUpdate(animation: ValueAnimator) {
                        val progress = animation.animatedValue as Float
                        view.alpha = AnimationUtils.lerp(
                            startValue,
                            endValue,
                            startFraction,
                            endFraction,
                            progress
                        )
                    }
                })

                val animatorSet = AnimatorSet().apply {
                    playTogether(fadeOutAnimator, fadeInAnimator, translationAnimatorSet)
                }
                animatorSet.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        super.onAnimationEnd(animation)
                        currentView.translationY = 0f
                        toView.translationY = 0f
                        currentView.alpha = 1f
                        toView.alpha = 1f
                    }
                })
                animatorSet.start()

                return true
            }
        })
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