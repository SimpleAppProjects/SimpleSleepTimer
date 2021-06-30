package com.thewizrd.simplesleeptimer

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
import androidx.core.util.Pair
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.transition.TransitionManager
import androidx.wear.widget.drawer.WearableDrawerLayout
import androidx.wear.widget.drawer.WearableDrawerView
import com.google.android.gms.wearable.MessageEvent
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.wearable.intent.RemoteIntent
import com.thewizrd.shared_resources.controls.TimerStartView
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.shared_resources.utils.*
import com.thewizrd.simplesleeptimer.controls.CustomConfirmationOverlay
import com.thewizrd.simplesleeptimer.databinding.ActivitySleeptimerBinding
import com.thewizrd.simplesleeptimer.helpers.ConfirmationResultReceiver
import kotlinx.coroutines.launch
import kotlin.math.max

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
                                        showProgressBar(false)
                                        showErrorMessage(true)
                                        binding.bottomActionDrawer.visibility = View.GONE
                                        closeBottomDrawer()
                                        binding.fab.visibility = View.GONE

                                        binding.messageView.setText(R.string.status_disconnected)
                                        binding.messageView.setOnClickListener(null)
                                    }
                                    WearConnectionStatus.APPNOTINSTALLED -> {
                                        showProgressBar(false)
                                        showErrorMessage(true)
                                        binding.bottomActionDrawer.visibility = View.GONE
                                        closeBottomDrawer()
                                        binding.fab.visibility = View.GONE

                                        binding.messageView.setText(R.string.error_sleeptimer_notinstalled)
                                        binding.messageView.setOnClickListener {
                                            val intentapp = Intent(Intent.ACTION_VIEW)
                                                .addCategory(Intent.CATEGORY_BROWSABLE)
                                                .setData(SleepTimerHelper.getPlayStoreURI())

                                            RemoteIntent.startRemoteActivity(
                                                this@SleepTimerActivity, intentapp,
                                                ConfirmationResultReceiver(this@SleepTimerActivity)
                                            )
                                        }
                                    }
                                    WearConnectionStatus.CONNECTED -> {
                                        showProgressBar(false)
                                        showErrorMessage(false)
                                        binding.bottomActionDrawer.visibility = View.VISIBLE
                                        binding.bottomActionDrawer.controller.peekDrawer()
                                        binding.bottomActionDrawer.clearAnimation()
                                        if (timerModel.isRunning || binding.timerStartView.getTimerProgress() >= 1) {
                                            binding.fab.post { binding.fab.show() }
                                        } else {
                                            binding.fab.post { binding.fab.hide() }
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

        val peekContainer = binding.root.findViewById<ViewGroup>(R.id.ws_drawer_view_peek_container)
        peekContainer.viewTreeObserver.addOnPreDrawListener(object :
            ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                peekContainer.viewTreeObserver.removeOnPreDrawListener(this)

                val peekContainerHeight = peekContainer.measuredHeight
                val iconSize =
                    peekContainer.context.resources.getDimensionPixelSize(R.dimen.ws_peek_view_icon_size)
                val topPadd =
                    peekContainer.context.resources.getDimensionPixelSize(R.dimen.ws_peek_view_top_padding)
                val botPadd =
                    peekContainer.context.resources.getDimensionPixelSize(R.dimen.ws_peek_view_bottom_padding)
                val totalSize = iconSize + topPadd + botPadd

                binding.fab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = max(peekContainerHeight, totalSize)
                }

                binding.timerStartView.setButtonFlowBottomMargin(binding.fab.marginBottom + binding.fab.customSize + botPadd)

                return true
            }
        })

        binding.fab.setOnClickListener {
            var toRun = false
            if (timerModel.isRunning) {
                requestSleepTimerStop()
                timerModel.stopTimer()
                toRun = false
            } else {
                requestSleepTimerStart()
                timerModel.startTimer()
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
                                    R.drawable.ic_full_sad
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

    override fun onStart() {
        super.onStart()
        // Update statuses
        showProgressBar(true)

        lifecycleScope.launch {
            updateConnectionStatus()
            requestTimerStatus()
        }
    }

    override fun onStop() {
        super.onStop()
        stopUpdatingTime()
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
        // Set up a new MaterialSharedAxis in the specified axis and direction.
        val transform = MaterialFadeThrough().apply {
            duration = resources.getInteger(android.R.integer.config_longAnimTime).toLong()
        }

        // Set BottomDrawer state before transitioning to avoid weird transition
        if (isRunning) {
            closeBottomDrawer()
        } else {
            peekBottomDrawer()
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

    private fun showErrorMessage(show: Boolean) {
        lifecycleScope.launch {
            binding.fragmentContainer.visibility = if (show) View.GONE else View.VISIBLE
            binding.messageView.visibility = if (show) View.VISIBLE else View.GONE
            if (!show && !binding.bottomActionDrawer.isOpened && !binding.fragmentContainer.hasFocus()) {
                binding.fragmentContainer.requestFocus()
            }
            binding.fragmentContainer.requestFocus()
        }
    }

    private fun showProgressBar(show: Boolean) {
        lifecycleScope.launch {
            binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        }
    }
}