package com.thewizrd.simplesleeptimer

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.PermissionChecker
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.services.BaseTimerService
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.simplesleeptimer.preferences.Settings
import com.thewizrd.simplesleeptimer.ui.SleepTimerApp
import com.thewizrd.simplesleeptimer.viewmodels.SelectedPlayerViewModel
import com.thewizrd.simplesleeptimer.viewmodels.TimerOperation
import com.thewizrd.simplesleeptimer.viewmodels.TimerViewModel
import com.thewizrd.simplesleeptimer.viewmodels.WearableListenerViewModel
import com.thewizrd.simplesleeptimer.viewmodels.WearableListenerViewModel.Companion.EXTRA_EVENTDATA
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Sleep Timer remote control activity for connected device
 */
class SleepTimerActivity : ComponentActivity() {
    private val timerViewModel: TimerViewModel by viewModels()
    private val selectedPlayerViewModel: SelectedPlayerViewModel by viewModels()
    private val timeKeeperModel: TimerModel by viewModels()
    private var timerUpdateJob: Job? = null

    private var handledIntent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installSplashScreen()

        timerViewModel.updateTimerState(isLocalTimer = false, isLoading = true)

        setContent {
            SleepTimerApp()
        }

        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (PermissionChecker.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PermissionChecker.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }

        lifecycleScope.launch {
            timerViewModel.timerEvents.collect {
                when (it) {
                    TimerOperation.START -> {
                        timeKeeperModel.timerLengthInMs =
                            timerViewModel.uiState.value.timerLengthInMs

                        requestSleepTimerStart()
                    }

                    TimerOperation.STOP -> timerViewModel.requestSleepTimerStop()

                    TimerOperation.EXTEND_1M -> {
                        timeKeeperModel.extend1Min()
                        timerViewModel.requestUpdateTimer(timeKeeperModel)
                    }

                    TimerOperation.EXTEND_5M -> {
                        timeKeeperModel.extend5Min()
                        timerViewModel.requestUpdateTimer(timeKeeperModel)
                    }

                    else -> { /* ignore */
                    }
                }
            }
        }

        lifecycleScope.launch {
            timerViewModel.eventFlow.collect { event ->
                when (event.eventType) {
                    WearableListenerViewModel.ACTION_UPDATECONNECTIONSTATUS -> {
                        val connectionStatus = WearConnectionStatus.valueOf(
                            event.data.getInt(
                                WearableListenerViewModel.EXTRA_CONNECTIONSTATUS,
                                0
                            )
                        )

                        if (connectionStatus == WearConnectionStatus.CONNECTED) {
                            launch {
                                delay(1000)
                                timerViewModel.updateTimerState(
                                    isLoading = false,
                                    isRunning = timeKeeperModel.isRunning
                                )
                            }
                        }
                    }

                    SleepTimerHelper.SleepTimerStatusPath, SleepTimerHelper.SleepTimerStartPath -> {
                        val data = JSONParser.deserializer(
                            event.data.getString(EXTRA_EVENTDATA),
                            TimerModel::class.java
                        )

                        data?.let {
                            // Add a second for latency
                            it.endTimeInMs += DateUtils.SECOND_IN_MILLIS

                            if (timeKeeperModel.isRunning || it.isRunning != timeKeeperModel.isRunning) {
                                timeKeeperModel.updateModel(it)
                                if (!it.isRunning && timeKeeperModel.timerLengthInMins <= 0) {
                                    timeKeeperModel.timerLengthInMins = Settings.getLastTimeSet()
                                }
                            }

                            timerViewModel.updateTimerState(timeKeeperModel)
                        } ?: return@collect

                        if (timeKeeperModel.isRunning) {
                            showTimerProgressView()
                        } else {
                            showTimerStartView()
                        }
                    }

                    SleepTimerHelper.SleepTimerStopPath -> {
                        timeKeeperModel.stopTimer()
                        showTimerStartView()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        handledIntent = true

        if (intent.hasExtra(BaseTimerService.EXTRA_TIME_IN_MINS)) {
            lifecycleScope.launch {
                supervisorScope {
                    timerViewModel.uiState.filterNot {
                        it.isLoading
                    }.collectLatest { state ->
                        intent.getIntExtra(BaseTimerService.EXTRA_TIME_IN_MINS, 0).takeIf { it > 0 }
                            ?.let {
                                if (!state.isRunning) {
                                    timeKeeperModel.timerLengthInMins = it
                                    requestSleepTimerStart()
                                }

                                // Cancel flow subscription
                                this.cancel()
                            }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stopUpdatingTime()
        timeKeeperModel.stopTimer()
    }

    private fun requestSleepTimerStart() {
        val selectedPlayer = selectedPlayerViewModel.selectedPlayer.value
        timerViewModel.requestSleepTimerStart(timeKeeperModel.timerLengthInMins, selectedPlayer)
    }

    private fun showTimerStartView() {
        stopUpdatingTime()
        timerViewModel.updateTimerState(timeKeeperModel)
    }

    private fun showTimerProgressView() {
        timerViewModel.updateTimerState(timeKeeperModel)
        startUpdatingTime()
    }

    private fun startUpdatingTime() {
        stopUpdatingTime()
        timerUpdateJob = lifecycleScope.launch {
            while (isActive) {
                // If no timers require continuous updates, avoid scheduling the next update.
                if (!timeKeeperModel.isRunning) {
                    break
                } else {
                    timerViewModel.updateTimerState(timeKeeperModel)
                }

                // Repeat for progress animation
                delay(50)
            }
        }
    }

    private fun stopUpdatingTime() {
        timerUpdateJob?.cancel()
    }
}