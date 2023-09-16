package com.thewizrd.simplesleeptimer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.PermissionChecker
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.thewizrd.shared_resources.services.BaseTimerService
import com.thewizrd.shared_resources.sleeptimer.TimerDataModel
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.simplesleeptimer.helpers.AcceptDenyDialog
import com.thewizrd.simplesleeptimer.services.TimerService
import com.thewizrd.simplesleeptimer.ui.SleepTimerApp
import com.thewizrd.simplesleeptimer.viewmodels.TimerOperation
import com.thewizrd.simplesleeptimer.viewmodels.TimerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Sleep Timer for local WearOS device
 */
class SleepTimerLocalActivity : AppCompatActivity() {
    private val timerViewModel: TimerViewModel by viewModels()
    private var timerUpdateJob: Job? = null

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

        installSplashScreen()

        timerViewModel.updateTimerState(isLocalTimer = true)

        setContent {
            SleepTimerApp()
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
                    TimerOperation.START -> startTimer()
                    TimerOperation.STOP -> stopTimer()

                    TimerOperation.EXTEND_1M -> {
                        if (mBound && mTimerBinder.isRunning()) {
                            mTimerBinder.extend1MinTimer()
                        }
                    }

                    TimerOperation.EXTEND_5M -> {
                        if (mBound && mTimerBinder.isRunning()) {
                            mTimerBinder.extend5MinTimer()
                        }
                    }

                    else -> { /* ignore */
                    }
                }
            }
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

    private fun stopTimer() {
        if (mBound && mTimerBinder.isRunning()) {
            mTimerBinder.cancelTimer()
        }

        showTimerStartView()
    }

    private fun startTimer() {
        if (mBound) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !BaseTimerService.checkExactAlarmsPermission(this)
            ) {
                AcceptDenyDialog.Builder(this) { _, which ->
                    if (which == DialogInterface.BUTTON_POSITIVE) {
                        runCatching {
                            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                        }.onFailure {
                            Log.e("SleepTimerActivity", "Error", it)
                        }
                    }
                }
                    .setMessage(R.string.message_alarms_permission)
                    .show()

                return
            }

            lifecycleScope.launch {
                val state = timerViewModel.uiState.value
                val timerLengthInMins = state.timerLengthInMs.let {
                    TimeUnit.MILLISECONDS.toMinutes(it)
                } ?: TimerModel.DEFAULT_TIME_MIN

                applicationContext.startService(
                    Intent(applicationContext, TimerService::class.java)
                        .setAction(BaseTimerService.ACTION_START_TIMER)
                        .putExtra(
                            BaseTimerService.EXTRA_TIME_IN_MINS,
                            timerLengthInMins.toInt()
                        )
                )

                showTimerProgressView()
            }
        }
    }

    private fun showTimerStartView() {
        stopUpdatingTime()
        timerViewModel.updateTimerState(isRunning = false)
    }

    private fun showTimerProgressView() {
        timerViewModel.updateTimerState(isRunning = true)
        startUpdatingTime()
    }

    private fun startUpdatingTime() {
        stopUpdatingTime()
        timerUpdateJob = lifecycleScope.launch {
            while (isActive) {
                val model = TimerDataModel.getDataModel()
                val startTime = SystemClock.elapsedRealtime()
                // If no timers require continuous updates, avoid scheduling the next update.
                if (!model.isRunning) {
                    break
                } else {
                    timerViewModel.updateTimerState(model.toModel())
                }
                val endTime = SystemClock.elapsedRealtime()

                delay(startTime + 20 - endTime)
            }
        }
    }

    private fun stopUpdatingTime() {
        timerUpdateJob?.cancel()
    }
}