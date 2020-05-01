package com.thewizrd.simplesleeptimer

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.ObjectsCompat
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.thewizrd.simplesleeptimer.databinding.ActivityMainBinding
import com.thewizrd.simplesleeptimer.services.TimerService
import com.thewizrd.simplesleeptimer.viewmodels.SleepTimerViewModel

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var mBottomSheetBehavior: BottomSheetBehavior<View>

    private lateinit var viewModel: SleepTimerViewModel

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
        val view = binding.root
        setContentView(view)
        setSupportActionBar(binding.topAppBar)

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

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomSheet, object :
            OnApplyWindowInsetsListener {
            override fun onApplyWindowInsets(
                v: View?,
                insets: WindowInsetsCompat?
            ): WindowInsetsCompat {
                mBottomSheetBehavior.expandedOffset = insets?.systemWindowInsetTop!!
                return insets
            }
        })

        binding.bottomSheet.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE

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
                            .putExtra(TimerService.EXTRA_TIME_IN_MINS, viewModel.progressTimeInMins)
                    )
                }
            } else {
                Snackbar.make(binding.root, "Service not bound", Snackbar.LENGTH_SHORT).show()
            }
        }

        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.NewInstanceFactory()
        ).get(SleepTimerViewModel::class.java)

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
            override fun onReceive(context: Context?, intent: Intent?) {
                if (ObjectsCompat.equals(intent?.action, TimerService.ACTION_START_TIMER)) {
                    val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                    if (fragment !is TimerProgressFragment) {
                        showTimerProgressFragment()
                    }
                } else if (ObjectsCompat.equals(intent?.action, TimerService.ACTION_CANCEL_TIMER)) {
                    val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                    if (fragment !is TimerStartFragment) {
                        showStartTimerFragment()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(TimerService.ACTION_START_TIMER)
            addAction(TimerService.ACTION_CANCEL_TIMER)
        }
        registerReceiver(
            mBroadcastReceiver, filter
        )
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
        if (mBottomSheetBehavior.state != BottomSheetBehavior.STATE_COLLAPSED) {
            mBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            return
        }
        super.onBackPressed()
    }

    override fun onPause() {
        unregisterReceiver(mBroadcastReceiver)
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        mBound = false
    }
}