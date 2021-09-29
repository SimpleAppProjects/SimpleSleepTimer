package com.thewizrd.simplesleeptimer

import android.animation.*
import android.annotation.SuppressLint
import android.content.*
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.animation.AnimationUtils
import com.thewizrd.shared_resources.controls.TimerStartView
import com.thewizrd.shared_resources.services.BaseTimerService
import com.thewizrd.shared_resources.sleeptimer.TimerDataModel
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.shared_resources.utils.ContextUtils.getAttrColor
import com.thewizrd.shared_resources.utils.ContextUtils.getOrientation
import com.thewizrd.simplesleeptimer.databinding.ActivityMainBinding
import com.thewizrd.simplesleeptimer.services.TimerService
import com.thewizrd.simplesleeptimer.utils.ActivityUtils.setFullScreen
import com.thewizrd.simplesleeptimer.utils.ActivityUtils.setTransparentWindow
import com.thewizrd.simplesleeptimer.wearable.WearPermissionsActivity

class SleepTimerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

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
                showBottomAppBar(false)
            } else {
                showTimerStartView()
                showBottomAppBar(true)
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

        val backgroundColor = getAttrColor(android.R.attr.colorBackground)
        val surfaceColor = getAttrColor(R.attr.colorSurface)
        window.setTransparentWindow(
            backgroundColor, Color.TRANSPARENT,
            if (getOrientation() == Configuration.ORIENTATION_PORTRAIT) {
                Color.TRANSPARENT
            } else {
                surfaceColor
            }
        )
        window.setFullScreen(getOrientation() == Configuration.ORIENTATION_PORTRAIT)

        binding.bottomAppBar.navigationIcon = binding.bottomAppBar.navigationIcon?.let {
            val tintable = DrawableCompat.wrap(it).mutate()
            DrawableCompat.setTint(tintable, getAttrColor(R.attr.colorOnSurface))
            tintable
        }

        binding.bottomAppBar.setNavigationOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, MusicPlayersFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.bottomAppBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.nav_wearpermissions -> {
                    startActivity(Intent(this, WearPermissionsActivity::class.java))
                    true
                }
                else -> false
            }
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
                animateBottomAppBar(toRun)
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
            showBottomAppBar(false)
        } else {
            showTimerStartView()
            showBottomAppBar(true)
        }
    }

    override fun onResume() {
        super.onResume()
        mBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                when (intent?.action) {
                    BaseTimerService.ACTION_START_TIMER -> {
                        showTimerProgressView()
                        showBottomAppBar(false)
                    }
                    BaseTimerService.ACTION_CANCEL_TIMER -> {
                        showTimerStartView()
                        showBottomAppBar(true)
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
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }
    }

    /* Views */
    private fun showTimerStartView() {
        stopUpdatingTime()

        binding.timerProgressView.visibility = View.GONE
        binding.timerStartView.visibility = View.VISIBLE
        dismissPlayersFragment()

        updateFab()
    }

    private fun showTimerProgressView() {
        binding.timerProgressView.visibility = View.VISIBLE
        binding.timerStartView.visibility = View.GONE
        dismissPlayersFragment()

        updateFab()

        startUpdatingTime()
    }

    private fun showBottomAppBar(show: Boolean) {
        animateBottomAppBar(!show)
    }

    private fun updateFab() {
        if (TimerDataModel.getDataModel().isRunning) {
            binding.fab.setImageResource(R.drawable.ic_stop)
        } else {
            binding.fab.setImageResource(R.drawable.ic_play_arrow)
        }
    }

    private fun dismissPlayersFragment() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        }
    }

    private fun animateToView(isRunning: Boolean) {
        dismissPlayersFragment()

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

    private fun animateBottomAppBar(isRunning: Boolean) {
        val view = binding.bottomAppBar
        view.clearAnimation()

        val startAlpha = if (isRunning) 1f else 0f
        val endAlpha = if (isRunning) 0f else 1f
        val animDuration = resources.getInteger(android.R.integer.config_longAnimTime).toLong()

        view.alpha = startAlpha
        view.visibility = View.VISIBLE

        view.animate()
            .alpha(endAlpha)
            .setDuration(animDuration / 2)
            .withEndAction {
                if (isRunning) {
                    view.visibility = View.INVISIBLE
                } else {
                    view.visibility = View.VISIBLE
                }
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