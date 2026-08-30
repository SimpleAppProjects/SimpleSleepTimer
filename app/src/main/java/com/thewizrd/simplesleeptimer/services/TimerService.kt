package com.thewizrd.simplesleeptimer.services

import android.app.Activity
import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.text.format.DateUtils
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Metric.TimeDifference.FORMAT_CHRONOMETER
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.thewizrd.shared_resources.services.BaseTimerService
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.TimerStringFormatter
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.SleepTimerActivity
import com.thewizrd.simplesleeptimer.preferences.Settings
import com.thewizrd.simplesleeptimer.wearable.WearableManager
import com.thewizrd.shared_resources.R as sharedRes

class TimerService : BaseTimerService() {
    companion object {
        const val NOT_CHANNEL_ID = "SimpleSleepTimer.timerservice"
        const val NOTIFICATION_ID = 1000
    }

    override val notificationId: Int
        get() = NOTIFICATION_ID
    override val notificationChannelId: String
        get() = NOT_CHANNEL_ID

    // Wearable
    private lateinit var mWearManager: WearableManager

    override fun onCreate() {
        super.onCreate()
        mWearManager = WearableManager(this)
    }

    override fun updateTimerNotification(model: TimerModel): Notification {
        val remainingTime = model.remainingTimeInMs

        return NotificationCompat.Builder(this, NOT_CHANNEL_ID)
            .setSmallIcon(sharedRes.drawable.ic_hourglass_empty)
            .setContentTitle(getString(sharedRes.string.title_sleeptimer)).apply {
                // Don't set color for Android S+ devices (Material You)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    color = ContextCompat.getColor(this@TimerService, sharedRes.color.colorPrimary)
                }
            }
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSound(null)
            .setSilent(true)
            .addAction(0, getString(android.R.string.cancel), getCancelIntent(this))
            .setContentIntent(getClickIntent(this))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .apply {
                val notifMgr = NotificationManagerCompat.from(this@TimerService)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN && notifMgr.canPostPromotedNotifications()) {
                    setStyle(
                        NotificationCompat.MetricStyle()
                            .addMetric(
                                NotificationCompat.Metric(
                                    NotificationCompat.Metric.TimeDifference.forTimer(
                                        SystemClock.elapsedRealtime() + remainingTime,
                                        FORMAT_CHRONOMETER
                                    ),
                                    getString(sharedRes.string.timer_remaining_multiple)
                                )
                            )
                    )
                    setRequestPromotedOngoing(true)
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val chronoBase = SystemClock.elapsedRealtime() + remainingTime

                        val remoteViews =
                            RemoteViews(packageName, R.layout.chronometer_notif_content)
                        remoteViews.setChronometerCountDown(R.id.chronometer, true)
                        remoteViews.setChronometer(
                            R.id.chronometer,
                            chronoBase,
                            null,
                            model.isRunning
                        )
                        setCustomContentView(remoteViews)
                    } else {
                        setContentText(
                            TimerStringFormatter.formatTimeRemaining(
                                this@TimerService, remainingTime
                            )
                        )
                    }
                    setStyle(NotificationCompat.DecoratedCustomViewStyle())
                }

                val remainingMinsMs =
                    model.remainingTimeInMs - (model.remainingTimeInMs % DateUtils.MINUTE_IN_MILLIS)
                if (remainingMinsMs < (TimerModel.MAX_TIME_IN_MINS - 1).times(DateUtils.MINUTE_IN_MILLIS)) {
                    addAction(
                        0,
                        "+" + TimerStringFormatter.getNumberFormattedQuantityString(
                            this@TimerService, sharedRes.plurals.minutes_short, 1
                        ),
                        getExtend1MinPendingIntent()
                    )
                }
                if (remainingMinsMs < (TimerModel.MAX_TIME_IN_MINS - 5).times(DateUtils.MINUTE_IN_MILLIS)) {
                    addAction(
                        0,
                        "+" + TimerStringFormatter.getNumberFormattedQuantityString(
                            this@TimerService, sharedRes.plurals.minutes_short, 5
                        ),
                        getExtend5MinPendingIntent()
                    )
                }
            }
            .build()
    }

    override fun getOnClickActivityClass(): Class<out Activity> {
        return SleepTimerActivity::class.java
    }

    override fun sendPublicTimerUpdate() {
        mWearManager.sendSleepTimerUpdate(getTimerModel())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            QSTileService.requestListeningState(this)
        }
    }

    override fun sendTimerStarted() {
        super.sendTimerStarted()
        mWearManager.sendSleepTimerStart(getTimerModel())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            QSTileService.requestListeningState(this)
        }
    }

    override fun sendTimerCancelled() {
        super.sendTimerCancelled()
        mWearManager.sendSleepCancelled()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            QSTileService.requestListeningState(this)
        }
    }

    override fun pauseSelectedMediaPlayer() {
        // Check if audio player preference exists
        val audioPlayerPref = Settings.getMusicPlayer()
        if (audioPlayerPref != null) {
            val data = audioPlayerPref.split("/")
            if (data.size == 2) {
                val pkgName = data[0]
                val activityName = data[1]

                if (pkgName.isNotBlank() && activityName.isNotBlank()) {
                    // Check if the app has a registered MediaButton BroadcastReceiver
                    val infos = packageManager.queryBroadcastReceivers(
                        Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkgName),
                        PackageManager.GET_RESOLVED_FILTER
                    )
                    var pauseKeyIntent: Intent? = null

                    for (info in infos) {
                        if (pkgName == info.activityInfo.packageName) {
                            val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)

                            pauseKeyIntent = Intent().apply {
                                component = ComponentName(
                                    info.activityInfo.packageName,
                                    info.activityInfo.name
                                )
                                action = Intent.ACTION_MEDIA_BUTTON
                                putExtra(Intent.EXTRA_KEY_EVENT, event)
                            }
                            break
                        }
                    }

                    if (pauseKeyIntent != null) {
                        runCatching {
                            applicationContext.sendBroadcast(pauseKeyIntent)
                        }.onFailure {
                            Logger.error("TimerService", it, "error sending pause intent")
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        mWearManager.unregister()
        super.onDestroy()
    }
}