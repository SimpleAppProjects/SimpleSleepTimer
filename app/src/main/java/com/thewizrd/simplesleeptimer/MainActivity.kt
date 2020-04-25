package com.thewizrd.simplesleeptimer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import com.thewizrd.simplesleeptimer.databinding.ActivityMainBinding
import com.thewizrd.simplesleeptimer.services.TimerService

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private lateinit var viewModel: SleepTimerViewModel
    private lateinit var mTimerBinder: TimerService.LocalBinder
    private var mBound: Boolean = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            mTimerBinder = service as TimerService.LocalBinder
            mBound = true
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

                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, TimerStartFragment())
                        .commit()
                } else {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, TimerProgressFragment())
                        .commit()

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
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, TimerStartFragment())
            .commit()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.id_settings -> {
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()
        Intent(this, TimerService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        mBound = false
    }
}
