package com.thewizrd.simplesleeptimer.wearable

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.google.android.gms.wearable.*
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.helpers.toImmutableCompatFlag
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.stringToBytes
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.SleepTimerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WearableDataListenerService : WearableListenerService() {
    companion object {
        private const val TAG = "WearableListenerService"

        private const val NOT_CHANNEL_ID = "SimpleSleepTimer.timerservice_remote"
        private const val NOTIFICATION_ID = 1001
        private const val REMOTE_TIMER_LOCUS_ID = "remote_timer"
    }

    @Volatile
    private var mPhoneNodeWithApp: Node? = null

    private lateinit var mNotificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        mNotificationManager = getSystemService(NotificationManager::class.java)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == WearableHelper.StartActivityPath) {
            val startIntent = Intent(this, SleepTimerActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            this.startActivity(startIntent)
        } else if (messageEvent.path == WearableHelper.BtDiscoverPath) {
            this.startActivity(
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 30)
            )

            GlobalScope.launch(Dispatchers.Default) {
                sendMessage(
                    messageEvent.sourceNodeId,
                    messageEvent.path,
                    Build.MODEL.stringToBytes()
                )
            }
        }
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        super.onDataChanged(dataEventBuffer)

        for (event in dataEventBuffer) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val item = event.dataItem
                if (item.uri.path == SleepTimerHelper.SleepTimerBridgePath) {
                    createTimerOngoingActivity(item)
                }
            }
            if (event.type == DataEvent.TYPE_DELETED) {
                val item = event.dataItem
                if (item.uri.path == SleepTimerHelper.SleepTimerBridgePath) {
                    dismissTimerOngoingActivity()
                }
            }
        }
    }

    private fun createTimerOngoingActivity(item: DataItem) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            initTimerNotifChannel()
        }

        val dataMap = DataMapItem.fromDataItem(item).dataMap
        val model = JSONParser.deserializer(
            dataMap.getString(SleepTimerHelper.KEY_TIMERDATA),
            TimerModel::class.java
        ) ?: return

        val notifBuilder = NotificationCompat.Builder(this, NOT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hourglass_empty)
            .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
            .setColorized(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSound(null)
            .setSilent(true)
            //.addAction(0, getString(android.R.string.cancel), getCancelIntent(this))
            .setContentIntent(getTimerIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocusId(LocusIdCompat(REMOTE_TIMER_LOCUS_ID))
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(model.endTimeInMs)

        val ongoingActivityStatus = Status.forPart(
            Status.TimerPart(SystemClock.elapsedRealtime() + model.remainingTimeInMs)
        )

        val ongoingActivity =
            OngoingActivity.Builder(
                applicationContext,
                NOTIFICATION_ID, notifBuilder
            )
                .setStaticIcon(R.drawable.ic_hourglass_empty)
                .setTitle(getString(R.string.title_sleeptimer_remote))
                .setStatus(ongoingActivityStatus)
                .setLocusId(LocusIdCompat(REMOTE_TIMER_LOCUS_ID))
                .build()

        ongoingActivity.apply(applicationContext)

        createTimerShortcut()
        mNotificationManager.notify(NOTIFICATION_ID, notifBuilder.build())
    }

    private fun getTimerIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this, NOTIFICATION_ID,
            Intent(this, SleepTimerActivity::class.java),
            0.toImmutableCompatFlag()
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initTimerNotifChannel() {
        var channel = mNotificationManager.getNotificationChannel(NOT_CHANNEL_ID)
        val notChannelName = getString(R.string.title_sleeptimer_remote)
        if (channel == null) {
            channel = NotificationChannel(
                NOT_CHANNEL_ID, notChannelName, NotificationManager.IMPORTANCE_DEFAULT
            )
        }

        // Configure channel
        channel.name = notChannelName
        mNotificationManager.createNotificationChannel(channel)
    }

    private fun createTimerShortcut() {
        val shortcut = ShortcutInfoCompat.Builder(this, REMOTE_TIMER_LOCUS_ID)
            .setShortLabel(getString(R.string.title_sleeptimer_remote))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_hourglass_simpleblue))
            .setIntent(
                Intent(this, SleepTimerActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
            )
            .setLocusId(LocusIdCompat(REMOTE_TIMER_LOCUS_ID))
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
    }

    private fun dismissTimerOngoingActivity() {
        NotificationManagerCompat.from(this)
            .cancel(NOTIFICATION_ID)
        removeTimerShortcut()
    }

    private fun removeTimerShortcut() {
        ShortcutManagerCompat.removeDynamicShortcuts(this, listOf(REMOTE_TIMER_LOCUS_ID))
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        mPhoneNodeWithApp = pickBestNodeId(capabilityInfo.nodes)
        if (mPhoneNodeWithApp == null) {
            // Disconnect or dismiss any ongoing activity
            dismissTimerOngoingActivity()
        }
    }

    private suspend fun sendMessage(nodeID: String, path: String, data: ByteArray?) {
        try {
            Wearable.getMessageClient(this@WearableDataListenerService)
                .sendMessage(nodeID, path, data).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
        }
    }

    /*
     * There should only ever be one phone in a node set (much less w/ the correct capability), so
     * I am just grabbing the first one (which should be the only one).
    */
    private fun pickBestNodeId(nodes: Collection<Node>): Node? {
        var bestNode: Node? = null

        // Find a nearby node/phone or pick one arbitrarily. Realistically, there is only one phone.
        for (node in nodes) {
            if (node.isNearby) {
                return node
            }
            bestNode = node
        }
        return bestNode
    }
}