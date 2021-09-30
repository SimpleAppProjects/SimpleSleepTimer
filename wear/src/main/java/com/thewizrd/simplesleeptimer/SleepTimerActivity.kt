package com.thewizrd.simplesleeptimer

import android.animation.*
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.util.Pair
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.wear.widget.drawer.WearableDrawerLayout
import androidx.wear.widget.drawer.WearableDrawerView
import com.google.android.gms.wearable.MessageEvent
import com.google.android.material.animation.AnimationUtils
import com.thewizrd.shared_resources.controls.TimerStartView
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.shared_resources.utils.*
import com.thewizrd.simplesleeptimer.controls.CustomConfirmationOverlay
import com.thewizrd.simplesleeptimer.databinding.ActivitySleeptimerBinding
import com.thewizrd.simplesleeptimer.helpers.showConfirmationOverlay
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.await
import kotlin.math.max

/**
 * Sleep Timer remote control activity for connected device
 */
class SleepTimerActivity : WearableListenerActivity() {
    override lateinit var broadcastReceiver: BroadcastReceiver
        private set
    override lateinit var intentFilter: IntentFilter
        private set

    private lateinit var binding: ActivitySleeptimerBinding

    private val timerModel: TimerModel by viewModels()
    private val selectedPlayer: SelectedPlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installSplashScreen()

        binding = ActivitySleeptimerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                lifecycleScope.launch {
                    if (intent.action != null) {
                        when (intent.action) {
                            ACTION_UPDATECONNECTIONSTATUS -> {
                                when (WearConnectionStatus.valueOf(
                                    intent.getIntExtra(
                                        EXTRA_CONNECTIONSTATUS,
                                        0
                                    )
                                )) {
                                    WearConnectionStatus.DISCONNECTED -> {
                                        // Navigate
                                        startActivity(
                                            Intent(
                                                this@SleepTimerActivity,
                                                PhoneSyncActivity::class.java
                                            )
                                        )
                                        finishAffinity()
                                    }
                                    WearConnectionStatus.APPNOTINSTALLED -> {
                                        val intentapp = Intent(Intent.ACTION_VIEW)
                                            .addCategory(Intent.CATEGORY_BROWSABLE)
                                            .setData(SleepTimerHelper.getPlayStoreURI())

                                        lifecycleScope.launch {
                                            runCatching {
                                                remoteActivityHelper.startRemoteActivity(intentapp)
                                                    .await()

                                                showConfirmationOverlay(true)
                                            }.onFailure {
                                                if (it !is CancellationException) {
                                                    showConfirmationOverlay(false)
                                                }
                                            }
                                        }
                                    }
                                    WearConnectionStatus.CONNECTED -> {
                                        launch {
                                            delay(1000)
                                            showProgressBar(false)
                                            binding.fragmentContainer.visibility = View.VISIBLE
                                            binding.bottomActionDrawer.visibility = View.VISIBLE
                                            if (timerModel.isRunning) {
                                                closeBottomDrawer()
                                            } else {
                                                peekBottomDrawer()
                                            }
                                            binding.bottomActionDrawer.clearAnimation()
                                            if (timerModel.isRunning || binding.timerStartView.getTimerProgress() >= 1) {
                                                binding.fab.post { binding.fab.show() }
                                            } else {
                                                binding.fab.post { binding.fab.hide() }
                                            }
                                        }
                                    }
                                }
                            }
                            WearableHelper.MusicPlayersPath -> {
                                if (connect()) {
                                    sendMessage(
                                        mPhoneNodeWithApp!!.id,
                                        WearableHelper.MusicPlayersPath,
                                        null
                                    )
                                }
                            }
                            else -> {
                                Log.println(
                                    Log.INFO,
                                    "SleepTimerActivity",
                                    "Unhandled action: ${intent.action}"
                                )
                            }
                        }
                    }
                }
            }
        }

        intentFilter = IntentFilter().apply {
            addAction(ACTION_UPDATECONNECTIONSTATUS)
            addAction(WearableHelper.MusicPlayersPath)
        }

        binding.drawerLayout.setDrawerStateCallback(object :
            WearableDrawerLayout.DrawerStateCallback() {
            override fun onDrawerOpened(
                layout: WearableDrawerLayout?,
                drawerView: WearableDrawerView?
            ) {
                super.onDrawerOpened(layout, drawerView)
                drawerView?.let { requestDrawerFocus(it, true) }
            }

            override fun onDrawerClosed(
                layout: WearableDrawerLayout?,
                drawerView: WearableDrawerView?
            ) {
                super.onDrawerClosed(layout, drawerView)
                drawerView?.let { requestDrawerFocus(it, false) }
                binding.fragmentContainer.requestFocus()
            }

            override fun onDrawerStateChanged(layout: WearableDrawerLayout?, newState: Int) {
                super.onDrawerStateChanged(layout, newState)
                if (newState == WearableDrawerView.STATE_IDLE) {
                    if (binding.bottomActionDrawer.isPeeking && !binding.fragmentContainer.hasFocus()) {
                        requestDrawerFocus(false)
                        binding.fragmentContainer.requestFocus()
                    }
                }
            }
        })
        binding.bottomActionDrawer.setIsLocked(false)
        binding.bottomActionDrawer.controller.peekDrawer()

        val peekContainer = binding.peekView
        peekContainer.viewTreeObserver.addOnPreDrawListener(object :
            ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                peekContainer.viewTreeObserver.removeOnPreDrawListener(this)

                val iconSize =
                    peekContainer.context.resources.getDimensionPixelSize(R.dimen.ws_peek_view_icon_size)
                val topPadd =
                    peekContainer.context.resources.getDimensionPixelSize(R.dimen.ws_peek_view_top_padding)
                val botPadd =
                    peekContainer.context.resources.getDimensionPixelSize(R.dimen.ws_peek_view_bottom_padding)
                val peekContainerHeight = peekContainer.measuredHeight + topPadd + botPadd
                val totalSize = iconSize + topPadd + botPadd

                binding.fab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = max(peekContainerHeight, totalSize)
                }

                binding.timerStartView.setButtonFlowBottomMargin(binding.fab.marginBottom + binding.fab.customSize + botPadd + topPadd)
                binding.timerProgressView.setButtonFlowBottomMargin(binding.fab.marginBottom + binding.fab.customSize + botPadd + topPadd)

                return true
            }
        })

        binding.fab.setOnClickListener {
            var toRun = false
            if (timerModel.isRunning) {
                timerModel.stopTimer()
                requestSleepTimerStop()
                toRun = false
            } else {
                timerModel.startTimer()
                requestSleepTimerStart()
                toRun = true
            }

            animateToView(toRun)
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
        binding.fab.post { binding.fab.visibility = View.GONE }

        binding.timerProgressView.setOnClickExtend1MinButtonListener {
            timerModel.extend1Min()
            requestUpdateTimer()
        }
        binding.timerProgressView.setOnClickExtend5MinButtonListener {
            timerModel.extend5Min()
            requestUpdateTimer()
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        lifecycleScope.launch {
            when (messageEvent.path) {
                SleepTimerHelper.SleepTimerStatusPath, SleepTimerHelper.SleepTimerStartPath -> {
                    val data = JSONParser.deserializer(
                        messageEvent.data.bytesToString(),
                        TimerModel::class.java
                    )

                    data?.let {
                        data.startTimeInMs =
                            data.startTimeInMs - (data.startTimeInMs % DateUtils.SECOND_IN_MILLIS)
                        data.endTimeInMs =
                            data.endTimeInMs - (data.endTimeInMs % DateUtils.SECOND_IN_MILLIS)
                        if (timerModel.isRunning || it.isRunning != timerModel.isRunning) {
                            timerModel.updateModel(it)
                            if (!it.isRunning && timerModel.timerLengthInMins <= 0) {
                                timerModel.timerLengthInMins = TimerModel.DEFAULT_TIME_MIN
                            }
                        }
                    } ?: return@launch

                    if (timerModel.isRunning) {
                        showTimerProgressView()
                    } else {
                        showTimerStartView()
                    }
                    binding.fragmentContainer.requestFocus()
                }
                SleepTimerHelper.SleepTimerStopPath -> {
                    timerModel.stopTimer()
                    showTimerStartView()
                }
                WearableHelper.OpenMusicPlayerPath -> {
                    val success = messageEvent.data.bytesToBool()
                    if (!success) {
                        CustomConfirmationOverlay()
                            .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                            .setCustomDrawable(
                                ContextCompat.getDrawable(
                                    this@SleepTimerActivity,
                                    R.drawable.ws_full_sad
                                )
                            )
                            .setMessage(this@SleepTimerActivity.getString(R.string.error_permissiondenied))
                            .showOn(this@SleepTimerActivity)

                        launch {
                            sendMessage(
                                messageEvent.sourceNodeId,
                                WearableHelper.StartPermissionsActivityPath,
                                null
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Update statuses
        showProgressBar(true)
        if (binding.bottomActionDrawer.isOpened) {
            closeBottomDrawer()
        }
        binding.bottomActionDrawer.visibility = View.INVISIBLE
        binding.fragmentContainer.visibility = View.GONE
        binding.fab.visibility = View.GONE

        lifecycleScope.launch {
            updateConnectionStatus()
            requestTimerStatus()
        }
    }

    override fun onStop() {
        super.onStop()
        stopUpdatingTime()
        timerModel.clearModel()
    }

    private suspend fun requestTimerStatus() {
        if (connect()) {
            sendMessage(mPhoneNodeWithApp!!.id, SleepTimerHelper.SleepTimerStatusPath, null)
        }
    }

    private fun requestSleepTimerStop() {
        lifecycleScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, SleepTimerHelper.SleepTimerStopPath, null)
            }
        }
    }

    private fun requestSleepTimerStart() {
        lifecycleScope.launch {
            if (connect()) {
                sendMessage(
                    mPhoneNodeWithApp!!.id, SleepTimerHelper.SleepTimerStartPath,
                    timerModel.timerLengthInMins.intToBytes()
                )

                if (selectedPlayer.keyValue != null) {
                    val data = selectedPlayer.keyValue!!.split("/").toTypedArray()
                    if (data.size == 2) {
                        val packageName = data[0]
                        val activityName = data[1]
                        sendMessage(
                            mPhoneNodeWithApp!!.id,
                            WearableHelper.OpenMusicPlayerPath,
                            JSONParser.serializer(
                                Pair.create(
                                    packageName,
                                    activityName
                                ), Pair::class.java
                            ).stringToBytes()
                        )
                    }
                }
            }
        }
    }

    private fun requestUpdateTimer() {
        lifecycleScope.launch {
            if (connect()) {
                sendMessage(
                    mPhoneNodeWithApp!!.id, SleepTimerHelper.SleepTimerUpdateStatePath,
                    JSONParser.serializer(timerModel, TimerModel::class.java).stringToBytes()
                )
            }
        }
    }

    /* Views */
    override fun onBackPressed() {
        if (binding.bottomActionDrawer.isOpened) {
            binding.bottomActionDrawer.controller.peekDrawer()
            return
        }
        super.onBackPressed()
    }

    private fun showTimerStartView() {
        stopUpdatingTime()

        binding.timerProgressView.visibility = View.GONE
        binding.timerStartView.visibility = View.VISIBLE
        peekBottomDrawer()
        binding.fragmentContainer.requestFocus()

        updateFab()
    }

    private fun showTimerProgressView() {
        binding.timerProgressView.visibility = View.VISIBLE
        binding.timerStartView.visibility = View.GONE
        closeBottomDrawer()
        binding.fragmentContainer.requestFocus()

        updateFab()

        startUpdatingTime()
    }

    private fun updateFab() {
        if (timerModel.isRunning) {
            binding.fab.setImageResource(R.drawable.ic_stop)
        } else {
            binding.fab.setImageResource(R.drawable.ic_play_arrow)
        }
    }

    private fun peekBottomDrawer() {
        binding.bottomActionDrawer.setIsLocked(false)
        binding.bottomActionDrawer.controller.peekDrawer()
    }

    private fun closeBottomDrawer() {
        binding.bottomActionDrawer.controller.closeDrawer()
        binding.bottomActionDrawer.setIsLocked(true)
    }

    private fun animateToView(isRunning: Boolean) {
        // Set BottomDrawer state before transitioning to avoid weird transition
        if (isRunning) {
            closeBottomDrawer()
        } else {
            peekBottomDrawer()
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
        override fun run() {
            val startTime = SystemClock.elapsedRealtime()
            // If no timers require continuous updates, avoid scheduling the next update.
            if (!timerModel.isRunning) {
                return
            } else {
                binding.timerProgressView.updateTimer(timerModel)
            }
            val endTime = SystemClock.elapsedRealtime()

            binding.fragmentContainer.postOnAnimationDelayed(this, startTime + 20 - endTime)
        }
    }

    private fun requestDrawerFocus(focus: Boolean) {
        requestDrawerFocus(binding.bottomActionDrawer, focus)
    }

    private fun requestDrawerFocus(drawer: WearableDrawerView, focus: Boolean) {
        drawer.descendantFocusability =
            if (focus) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
        if (focus) {
            drawer.requestFocus()
        } else {
            drawer.clearFocus()
        }
    }

    private fun showProgressBar(show: Boolean) {
        lifecycleScope.launch {
            binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        }
    }
}