package com.thewizrd.simplesleeptimer

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.simplesleeptimer.databinding.ActivityMainBinding
import com.thewizrd.simplesleeptimer.services.TimerService
import com.thewizrd.simplesleeptimer.utils.ActivityUtils

class MainActivity : AppCompatActivity() {
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
                showTimerProgressFragment()
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
                if (mTimerBinder.isRunning()) {
                    mTimerBinder.cancelTimer()

                    showStartTimerFragment()
                } else {
                    showTimerProgressFragment()

                    applicationContext.startService(
                        Intent(applicationContext, TimerService::class.java)
                            .setAction(TimerService.ACTION_START_TIMER)
                            .putExtra(TimerService.EXTRA_TIME_IN_MINS, timerModel.timerLengthInMins)
                    )
                }
            }
        }

        // Start fragment
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (fragment == null) {
            showStartTimerFragment()
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, TimerService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onResume() {
        super.onResume()
        mBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

                when (intent?.action) {
                    TimerService.ACTION_START_TIMER -> {
                        if (fragment !is TimerProgressFragment) {
                            showTimerProgressFragment()
                        }
                    }
                    TimerService.ACTION_CANCEL_TIMER -> {
                        if (fragment !is TimerStartFragment) {
                            showStartTimerFragment()
                        }
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

    private fun showStartTimerFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, TimerStartFragment())
            .commit()
        mBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        mBottomSheetBehavior.isHideable = false
    }

    private fun showTimerProgressFragment() {
        mBottomSheetBehavior.isHideable = true
        mBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, TimerProgressFragment())
            .commit()
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

    override fun onPause() {
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(mBroadcastReceiver)
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        mBound = false
    }
}