package com.thewizrd.simplesleeptimer.services

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.*
import android.text.format.DateUtils
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.thewizrd.simplesleeptimer.*
import com.thewizrd.simplesleeptimer.model.TimerDataModel
import com.thewizrd.simplesleeptimer.preferences.Settings
import com.thewizrd.simplesleeptimer.wearable.WearableManager
import java.util.*

class TimerService : Service() {
    companion object {
        const val NOT_CHANNEL_ID = "SimpleSleepTimer.timerservice"
        const val NOTIFICATION_ID = 1000

        const val ACTION_START_TIMER = "SimpleSleepTimer.action.START_TIMER"
        const val ACTION_UPDATE_TIMER = "SimpleSleepTimer.action.UPDATE_TIMER"
        const val ACTION_CANCEL_TIMER = "SimpleSleepTimer.action.CANCEL_TIMER"
        private const val ACTION_EXPIRE_TIMER = "SimpleSleepTimer.action.EXPIRE_TIMER"

        const val EXTRA_TIME_IN_MINS = "SimpleSleepTimer.extra.TIME_IN_MINUTES"
    }

    private lateinit var mLocalBroadcastMgr: LocalBroadcastManager
    private lateinit var mAlarmManager: AlarmManager

    private var mForegroundNotification: Notification? = null
    private var mIsBound: Boolean = false

    // Wearable
    private lateinit var mWearManager: WearableManager

    // Binder given to clients
    private val binder = LocalBinder()

    // Timer
    private val model = TimerDataModel.getDataModel()

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            initChannel()
        }
        startForegroundIfNeeded()

        mLocalBroadcastMgr = LocalBroadcastManager.getInstance(this)
        mAlarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        mWearManager = WearableManager(this)
    }

    private fun startForegroundIfNeeded() {
        if (!mIsBound) {
            startForeground(NOTIFICATION_ID, getForegroundNotification())
            updateNotification()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initChannel() {
        val context = this.applicationContext
        val mNotifyMgr =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        var mChannel = mNotifyMgr.getNotificationChannel(NOT_CHANNEL_ID)
        val notChannelName = context.getString(R.string.not_channel_name_timer)

        if (mChannel == null) {
            mChannel = NotificationChannel(
                NOT_CHANNEL_ID,
                notChannelName,
                NotificationManager.IMPORTANCE_LOW
            )
        }

        // Configure the notification channel
        mChannel.name = notChannelName
        mChannel.setShowBadge(false)
        mChannel.enableLights(false)
        mChannel.enableVibration(false)
        mChannel.setSound(null, null)
        mNotifyMgr.createNotificationChannel(mChannel)
    }

    private fun getForegroundNotification(): Notification {
        if (mForegroundNotification == null) {
            mForegroundNotification =
                NotificationCompat.Builder(this, NOT_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_hourglass_empty)
                    .setContentTitle(getString(R.string.title_sleeptimer))
                    .setContentText("--:--:--")
                    .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setSound(null)
                    .addAction(
                        0,
                        getString(android.R.string.cancel),
                        getCancelIntent(this)
                    )
                    .setContentIntent(getClickIntent(this))
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
        }

        return mForegroundNotification!!
    }

    private fun updateNotification() {
        val remainingTime = model.remainingTimeInMs

        mForegroundNotification =
            NotificationCompat.Builder(this, NOT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_hourglass_empty)
                .setContentTitle(getString(R.string.title_sleeptimer))
                .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setSound(null)
                .addAction(
                    0,
                    getString(android.R.string.cancel),
                    getCancelIntent(this)
                )
                .setContentIntent(getClickIntent(this))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .apply {
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
                }
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .build()

        NotificationManagerCompat.from(this)
            .notify(NOTIFICATION_ID, mForegroundNotification!!)

        if (model.isRunning && remainingTime > DateUtils.MINUTE_IN_MILLIS) {
            val pi = getUpdateIntent()

            val nextMinuteChange = remainingTime % DateUtils.MINUTE_IN_MILLIS
            val triggerTime = System.currentTimeMillis() + nextMinuteChange
            schedulePendingIntent(pi, triggerTime)
        } else {
            val pi = getUpdateIntent(true)
            if (pi != null) {
                mAlarmManager.cancel(pi)
                pi.cancel()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundIfNeeded()

        when (intent?.action) {
            ACTION_START_TIMER -> {
                if (intent.hasExtra(EXTRA_TIME_IN_MINS)) {
                    val timeInMin = intent.getIntExtra(EXTRA_TIME_IN_MINS, 0)
                    if (timeInMin > 0) {
                        startTimer(timeInMin)
                    }
                }
            }
            ACTION_UPDATE_TIMER -> {
                updateTimer()
            }
            ACTION_CANCEL_TIMER -> {
                cancelTimer()
            }
            ACTION_EXPIRE_TIMER -> {
                expireTimer()
            }
        }

        return START_STICKY
    }

    private fun startTimer(timeInMin: Int) {
        model.startTimer(timeInMin)
        sendTimerStarted()

        // Setup expire intent
        updateExpireIntent()

        // Update timer notification
        updateTimer()
    }

    private fun updateTimer() {
        if (!mIsBound) {
            updateNotification()
        } else {
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        }
    }

    private fun expireTimer() {
        pauseMusicAction()
        cancelTimer()
    }

    private fun cancelTimer() {
        cancelUpdateIntent()
        cancelExpireIntent()
        if (model.isRunning) {
            sendTimerCancelled()
            model.stopTimer()
        }
        stopForeground(true)
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun sendPublicTimerUpdate(startTimeInMs: Long, millisUntilFinish: Long) {
        mWearManager.sendSleepTimerUpdate(startTimeInMs, millisUntilFinish)
    }

    private fun sendTimerStarted() {
        mLocalBroadcastMgr.sendBroadcast(
            Intent(ACTION_START_TIMER)
        )
        mWearManager.sendSleepTimerStart()
    }

    private fun sendTimerCancelled() {
        mLocalBroadcastMgr.sendBroadcast(
            Intent(ACTION_CANCEL_TIMER)
        )
        mWearManager.sendSleepCancelled()
    }

    private fun getCancelIntent(context: Context): PendingIntent {
        val intent = Intent(context.applicationContext, TimerService::class.java)
            .setAction(ACTION_CANCEL_TIMER)

        return PendingIntent.getService(
            context.applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun getClickIntent(context: Context): PendingIntent {
        val intent = Intent(context.applicationContext, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

        return PendingIntent.getActivity(
            context.applicationContext,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun getUpdateIntent(): PendingIntent {
        return getUpdateIntent(false)!!
    }

    private fun getUpdateIntent(cancel: Boolean = false): PendingIntent? {
        val flags = if (cancel) {
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_NO_CREATE
        } else {
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT
        }

        val i = Intent(this, TimerService::class.java)
            .setAction(ACTION_UPDATE_TIMER)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, 0, i, flags)
        } else {
            PendingIntent.getService(this, 0, i, flags)
        }
    }

    private fun cancelUpdateIntent() {
        val pi = getUpdateIntent(true)
        if (pi != null) {
            mAlarmManager.cancel(pi)
            pi.cancel()
        }
    }

    private fun getExpireIntent(): PendingIntent {
        return getExpireIntent(false)!!
    }

    private fun getExpireIntent(cancel: Boolean = false): PendingIntent? {
        val flags = if (cancel) {
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_NO_CREATE
        } else {
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT
        }

        val i = Intent(this, TimerService::class.java)
            .setAction(ACTION_EXPIRE_TIMER)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, 0, i, flags)
        } else {
            PendingIntent.getService(this, 0, i, flags)
        }
    }

    private fun updateExpireIntent() {
        scheduleExpirePendingIntent(getExpireIntent())
    }

    private fun cancelExpireIntent() {
        val pi = getExpireIntent(true)
        if (pi != null) {
            mAlarmManager.cancel(pi)
            pi.cancel()
        }
    }

    private fun scheduleExpirePendingIntent(pi: PendingIntent) {
        schedulePendingIntent(pi, model.endTimeInMs)
    }

    private fun schedulePendingIntent(pi: PendingIntent, rtcExpireTimeInMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, rtcExpireTimeInMs, pi)
        } else {
            mAlarmManager.setExact(AlarmManager.RTC_WAKEUP, rtcExpireTimeInMs, pi)
        }
    }

    private fun pauseMusicAction() {
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

        // Send pause event to which ever player has audio focus
        val audioMan = this.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
        audioMan.dispatchMediaKeyEvent(event)
    }

    override fun onBind(intent: Intent?): IBinder {
        // Background restrictions don't apply to bound services
        // We can remove the notification now
        mIsBound = true
        stopForeground(true)

        return binder
    }

    inner class LocalBinder : Binder() {
        fun isRunning(): Boolean = model.isRunning

        fun cancelTimer() {
            this@TimerService.cancelTimer()
        }

        fun startTimer(timeInMin: Int) {
            this@TimerService.startTimer(timeInMin)
        }
    }

    override fun onDestroy() {
        cancelTimer()
        mWearManager.unregister()
        super.onDestroy()
        stopForeground(true)
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        mIsBound = true

        // Background restrictions don't apply to bound services
        // We can remove the notification now
        stopForeground(true)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // Since service is unbound, background restrictions now apply
        // Start foreground to notify system
        mIsBound = false
        startForegroundIfNeeded()

        return true // Allow re-binding
    }
}