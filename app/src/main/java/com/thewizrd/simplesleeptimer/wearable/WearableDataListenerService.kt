package com.thewizrd.simplesleeptimer.wearable

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.utils.bytesToInt
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.simplesleeptimer.SleepTimerActivity
import com.thewizrd.simplesleeptimer.services.TimerService

class WearableDataListenerService : WearableListenerService() {
    companion object {
        private const val TAG = "WearableDataListenerService"
        const val ACTION_GETCONNECTEDNODE = "SimpleWear.Droid.action.GET_CONNECTED_NODE"
        const val EXTRA_NODEDEVICENAME = "SimpleWear.Droid.extra.NODE_DEVICE_NAME"
    }

    private lateinit var mWearMgr: WearableManager

    override fun onCreate() {
        super.onCreate()
        mWearMgr = WearableManager(this)
    }

    override fun onDestroy() {
        mWearMgr.unregister()
        super.onDestroy()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearableHelper.StartActivityPath -> {
                val startIntent = Intent(this, SleepTimerActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(startIntent)
            }
            WearableHelper.StartPermissionsActivityPath -> {
                val startIntent = Intent(this, WearPermissionsActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(startIntent)
            }
            WearableHelper.MusicPlayersPath -> {
                WearableWorker.sendSupportedMusicPlayers(this)
            }
            WearableHelper.OpenMusicPlayerPath -> {
                val jsonData = messageEvent.data.bytesToString()
                WearableWorker.startMusicPlayer(this, messageEvent.sourceNodeId, jsonData)
            }
            WearableHelper.BtDiscoverPath -> {
                val deviceName = messageEvent.data.bytesToString()
                LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(
                        Intent(ACTION_GETCONNECTEDNODE)
                            .putExtra(EXTRA_NODEDEVICENAME, deviceName)
                    )
            }
            SleepTimerHelper.SleepTimerStartPath -> {
                val timeInMins = messageEvent.data.bytesToInt()
                timeInMins?.let { startSleepTimer(it) }
            }
            SleepTimerHelper.SleepTimerStopPath -> {
                stopSleepTimer()
            }
            SleepTimerHelper.SleepTimerStatusPath -> {
                WearableWorker.sendSleepTimerStatus(this)
            }
        }
    }

    private fun startSleepTimer(timeInMins: Int) {
        val startTimerIntent = Intent(this, TimerService::class.java)
            .setAction(SleepTimerHelper.ACTION_START_TIMER)
            .putExtra(SleepTimerHelper.EXTRA_TIME_IN_MINS, timeInMins)
        ContextCompat.startForegroundService(this, startTimerIntent)
    }

    private fun stopSleepTimer() {
        val stopTimerIntent = Intent(this, TimerService::class.java)
            .setAction(SleepTimerHelper.ACTION_CANCEL_TIMER)
        ContextCompat.startForegroundService(this, stopTimerIntent)
    }
}