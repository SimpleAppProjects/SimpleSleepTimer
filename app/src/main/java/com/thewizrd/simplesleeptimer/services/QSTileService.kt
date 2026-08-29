package com.thewizrd.simplesleeptimer.services

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.service.quicksettings.PendingIntentActivityWrapper
import androidx.core.service.quicksettings.TileServiceCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.thewizrd.shared_resources.services.BaseTimerService
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.sleeptimer.TimerDataModel
import com.thewizrd.simplesleeptimer.SleepTimerActivity
import com.thewizrd.shared_resources.R as sharedRes

@RequiresApi(Build.VERSION_CODES.N)
class QSTileService : TileService() {
    companion object {
        @RequiresApi(Build.VERSION_CODES.N)
        fun requestListeningState(context: Context) {
            requestListeningState(
                context.applicationContext,
                ComponentName(context.applicationContext, QSTileService::class.java)
            )
        }
    }

    private lateinit var mLocalBroadcastMgr: LocalBroadcastManager
    private lateinit var mBroadcastReceiver: BroadcastReceiver
    private lateinit var mIntentFilter: IntentFilter

    override fun onCreate() {
        super.onCreate()
        mLocalBroadcastMgr = LocalBroadcastManager.getInstance(this)
        mIntentFilter = IntentFilter().apply {
            addAction(BaseTimerService.ACTION_START_TIMER)
            addAction(BaseTimerService.ACTION_CANCEL_TIMER)
        }
        mBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                when (intent?.action) {
                    BaseTimerService.ACTION_START_TIMER, BaseTimerService.ACTION_CANCEL_TIMER -> {
                        updateState()
                    }
                }
            }
        }
        mLocalBroadcastMgr.registerReceiver(mBroadcastReceiver, mIntentFilter)
    }

    override fun onDestroy() {
        mLocalBroadcastMgr.unregisterReceiver(mBroadcastReceiver)
        super.onDestroy()
    }

    override fun onStartListening() {
        super.onStartListening()
        // Keep tile state up-to-date
        updateState()
    }

    override fun onStopListening() {
        // Stop UI updates
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (!TimerDataModel.getDataModel().isRunning) {
            startTimerActivity()
        } else {
            val stopTimerIntent = Intent(this, TimerService::class.java)
                .setAction(SleepTimerHelper.ACTION_CANCEL_TIMER)
            BaseTimerService.enqueueWork(this, stopTimerIntent)
        }
    }

    private fun startTimerActivity() {
        val pi = PendingIntentActivityWrapper(
            this,
            this.hashCode(),
            Intent(this, SleepTimerActivity::class.java),
            0,
            false
        )
        TileServiceCompat.startActivityAndCollapse(this, pi)
    }

    private fun updateState() {
        val tile = qsTile
        val model = TimerDataModel.getDataModel()
        if (model.isRunning) {
            tile.state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(sharedRes.string.label_stop)
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(sharedRes.string.label_start)
            }
        }
        tile.updateTile()
    }
}