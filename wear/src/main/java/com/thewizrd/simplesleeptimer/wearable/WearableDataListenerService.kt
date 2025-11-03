package com.thewizrd.simplesleeptimer.wearable

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.SystemClock
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
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.WearableListenerService
import com.thewizrd.shared_resources.appLib
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.helpers.toImmutableCompatFlag
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.SleepTimerActivity
import com.thewizrd.simplesleeptimer.datastore.remoteTimerDataStore
import com.thewizrd.simplesleeptimer.wearable.tiles.SleepTimerTileService
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

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
        when (messageEvent.path) {
            WearableHelper.StartActivityPath -> {
                val startIntent = Intent(this, SleepTimerActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                this.startActivity(startIntent)
            }

            SleepTimerHelper.SleepTimerStartPath,
            SleepTimerHelper.SleepTimerStatusPath -> {
                val jsonData = messageEvent.data?.bytesToString()
                val model = jsonData?.let {
                    JSONParser.deserializer(it, TimerModel::class.java)
                }

                appLib.appScope.launch {
                    runCatching {
                        val tileDataStore = appLib.context.remoteTimerDataStore
                        val currentState = tileDataStore.data.firstOrNull()

                        tileDataStore.updateData { cache ->
                            cache.copy(
                                isLocalTimer = false,
                                timerModel = model?.apply {
                                    // Add a second for latency
                                    endTimeInMs += 500
                                    updateModel(this)
                                }
                            )
                        }

                        if (model?.isRunning != currentState?.timerModel?.isRunning ||
                            model?.endTimeInMs != currentState?.timerModel?.endTimeInMs
                        ) {
                            SleepTimerTileService.requestTileUpdate(this@WearableDataListenerService)
                        }
                    }
                }
            }

            SleepTimerHelper.SleepTimerStopPath -> {
                appLib.appScope.launch {
                    runCatching {
                        val tileDataStore = appLib.context.remoteTimerDataStore

                        tileDataStore.updateData { cache ->
                            cache.copy(
                                isLocalTimer = false,
                                timerModel = cache.timerModel?.let {
                                    it.stopTimer()
                                    TimerModel().apply {
                                        updateModel(it)
                                    }
                                }
                            )
                        }

                        SleepTimerTileService.requestTileUpdate(this@WearableDataListenerService)
                    }
                }
            }

            SleepTimerHelper.SleepTimerBridgePath -> {
                val jsonData = messageEvent.data?.bytesToString()
                val model = jsonData?.let {
                    JSONParser.deserializer(it, TimerModel::class.java)
                }

                if (model != null) {
                    createTimerOngoingActivity(model)
                } else {
                    dismissTimerOngoingActivity()
                }
            }
        }
    }

    private fun createTimerOngoingActivity(model: TimerModel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            initTimerNotifChannel()
        }

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
                .setAnimatedIcon(R.drawable.avd_hourglass_rotate)
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