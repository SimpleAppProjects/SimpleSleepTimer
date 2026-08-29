package com.thewizrd.simplesleeptimer.wearable

import android.content.Intent
import android.os.Build
import androidx.core.util.Pair
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.services.BaseTimerService
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.sleeptimer.TimerDataModel
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.bytesToInt
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.shared_resources.utils.longToBytes
import com.thewizrd.simplesleeptimer.SleepTimerActivity
import com.thewizrd.simplesleeptimer.services.TimerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

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
        runBlocking(Dispatchers.Default) {
            val ctx = this@WearableDataListenerService

            if (messageEvent.path == WearableHelper.StartActivityPath) {
                val startIntent = Intent(ctx, SleepTimerActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(startIntent)
            } else if (messageEvent.path == WearableHelper.StartPermissionsActivityPath) {
                val startIntent = Intent(ctx, WearPermissionsActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(startIntent)
            } else if (messageEvent.path == WearableHelper.MusicPlayersPath) {
                mWearMgr.sendSupportedMusicPlayers(messageEvent.sourceNodeId)
            } else if (messageEvent.path == WearableHelper.OpenMusicPlayerPath) {
                val jsonData = messageEvent.data.bytesToString()
                val pair = JSONParser.deserializer(jsonData, Pair::class.java)
                val pkgName = pair?.first.toString()
                val activityName = pair?.second.toString()
                mWearMgr.startMusicPlayer(messageEvent.sourceNodeId, pkgName, activityName)
            } else if (messageEvent.path == SleepTimerHelper.SleepTimerStartPath) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || BaseTimerService.checkExactAlarmsPermission(
                        ctx
                    )
                ) {
                    val timeInMins = messageEvent.data.bytesToInt()
                    timeInMins?.let { startSleepTimer(it) }
                } else {
                    mWearMgr.sendMessage(
                        messageEvent.sourceNodeId, SleepTimerHelper.SleepTimerPermDeniedPath,
                        null
                    )
                }
            } else if (messageEvent.path == SleepTimerHelper.SleepTimerStopPath) {
                stopSleepTimer()
            } else if (messageEvent.path == SleepTimerHelper.SleepTimerStatusPath) {
                mWearMgr.sendSleepTimerUpdate(
                    messageEvent.sourceNodeId,
                    TimerDataModel.getDataModel().toModel()
                )
            } else if (messageEvent.path == SleepTimerHelper.SleepTimerUpdateStatePath) {
                val jsonData = messageEvent.data.bytesToString()
                val model = JSONParser.deserializer(jsonData, TimerModel::class.java)

                if (model != null) {
                    TimerDataModel.getDataModel().updateModel(model)
                    updateSleepTimer()
                }
            } else if (messageEvent.path == WearableHelper.VersionPath) {
                mWearMgr.sendMessage(
                    messageEvent.sourceNodeId, messageEvent.path,
                    WearableHelper.getAppVersionCode().longToBytes()
                )
            }
        }
    }

    private fun startSleepTimer(timeInMins: Int) {
        val startTimerIntent = Intent(this, TimerService::class.java)
            .setAction(SleepTimerHelper.ACTION_START_TIMER)
            .putExtra(SleepTimerHelper.EXTRA_TIME_IN_MINS, timeInMins)
        BaseTimerService.enqueueWork(this, startTimerIntent)
    }

    private fun stopSleepTimer() {
        val stopTimerIntent = Intent(this, TimerService::class.java)
            .setAction(SleepTimerHelper.ACTION_CANCEL_TIMER)
        BaseTimerService.enqueueWork(this, stopTimerIntent)
    }

    private fun updateSleepTimer() {
        val i = Intent(this, TimerService::class.java)
            .setAction(BaseTimerService.ACTION_UPDATE_TIMER)
        BaseTimerService.enqueueWork(this, i)
    }
}