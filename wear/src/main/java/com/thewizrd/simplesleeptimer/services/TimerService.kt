package com.thewizrd.simplesleeptimer.services

import android.app.*
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.text.format.DateUtils
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.thewizrd.shared_resources.services.BaseTimerService
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.shared_resources.utils.TimerStringFormatter
import com.thewizrd.simplesleeptimer.*
import com.thewizrd.simplesleeptimer.preferences.Settings
import kotlinx.coroutines.*
import java.util.*

class TimerService : BaseTimerService() {
    companion object {
        const val NOT_CHANNEL_ID = "SimpleSleepTimer.timerservice"
        const val NOTIFICATION_ID = 1000
        private const val LOCAL_TIMER_LOCUS_ID = "local_timer"
    }

    override val notificationId: Int
        get() = NOTIFICATION_ID
    override val notificationChannelId: String
        get() = NOT_CHANNEL_ID

    override fun onCreate() {
        super.onCreate()
    }

    override fun updateTimerNotification(model: TimerModel): Notification {
        val remainingTime = model.remainingTimeInMs

        val notifBuilder = NotificationCompat.Builder(this, NOT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hourglass_empty)
            //.setContentTitle(getString(R.string.title_sleeptimer))
            .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
            .setColorized(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSound(null)
            .addAction(0, getString(android.R.string.cancel), getCancelIntent(this))
            .setContentIntent(getClickIntent(this))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocusId(LocusIdCompat(LOCAL_TIMER_LOCUS_ID))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                    setWhen(model.endTimeInMs)
                } else {
                    setContentText(
                        TimerStringFormatter.formatTimeRemaining(
                            this@TimerService, remainingTime
                        )
                    )
                }

                val remainingMinsMs =
                    model.remainingTimeInMs - (model.remainingTimeInMs % DateUtils.MINUTE_IN_MILLIS)
                if (remainingMinsMs < (TimerModel.MAX_TIME_IN_MINS - 1).times(DateUtils.MINUTE_IN_MILLIS)) {
                    addAction(
                        0,
                        "+" + TimerStringFormatter.getNumberFormattedQuantityString(
                            this@TimerService, R.plurals.minutes_short, 1
                        ),
                        getExtend1MinPendingIntent()
                    )
                }
                if (remainingMinsMs < (TimerModel.MAX_TIME_IN_MINS - 5).times(DateUtils.MINUTE_IN_MILLIS)) {
                    addAction(
                        0,
                        "+" + TimerStringFormatter.getNumberFormattedQuantityString(
                            this@TimerService, R.plurals.minutes_short, 5
                        ),
                        getExtend5MinPendingIntent()
                    )
                }
            }

        if (model.isRunning) {
            val ongoingActivityStatus = Status.forPart(
                Status.TimerPart(SystemClock.elapsedRealtime() + model.remainingTimeInMs)
            )

            val ongoingActivity =
                OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, notifBuilder)
                    .setStaticIcon(R.drawable.ic_hourglass_empty)
                    .setTitle(getString(R.string.title_sleeptimer))
                    .setStatus(ongoingActivityStatus)
                    .setLocusId(LocusIdCompat(LOCAL_TIMER_LOCUS_ID))
                    .build()

            ongoingActivity.apply(applicationContext)
        }

        return notifBuilder.build()
    }

    private fun createTimerShortcut() {
        val shortcut = ShortcutInfoCompat.Builder(this, LOCAL_TIMER_LOCUS_ID)
            .setShortLabel(getString(R.string.title_sleeptimer))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_hourglass_empty))
            .setIntent(
                Intent(this, getOnClickActivityClass())
                    .setAction(Intent.ACTION_VIEW)
            )
            .setLocusId(LocusIdCompat(LOCAL_TIMER_LOCUS_ID))
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
    }

    override fun sendTimerStarted() {
        super.sendTimerStarted()
        createTimerShortcut()
    }

    override fun getOnClickActivityClass(): Class<out Activity> {
        return SleepTimerLocalActivity::class.java
    }

    override fun sendPublicTimerUpdate() {}

    override fun sendTimerCancelled() {
        super.sendTimerCancelled()
        ShortcutManagerCompat.removeDynamicShortcuts(this, listOf(LOCAL_TIMER_LOCUS_ID))
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
                                putExtra(Intent.EXTRA_KEY_EVENT, event)
                                action = Intent.ACTION_MEDIA_BUTTON
                                component = ComponentName(pkgName, info.activityInfo.name)
                            }
                            break
                        }
                    }

                    if (pauseKeyIntent != null) {
                        sendBroadcast(pauseKeyIntent)
                    }
                }
            }
        }
    }
}