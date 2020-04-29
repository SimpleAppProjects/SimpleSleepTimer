package com.thewizrd.simplesleeptimer

import android.content.*
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.ObjectsCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import com.thewizrd.simplesleeptimer.databinding.ActivityMainBinding
import com.thewizrd.simplesleeptimer.services.TimerService
import com.thewizrd.simplesleeptimer.viewmodels.SleepTimerViewModel

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

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
    }

    private fun showTimerProgressFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, TimerProgressFragment())
            .commit()
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