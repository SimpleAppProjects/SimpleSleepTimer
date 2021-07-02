package com.thewizrd.simplesleeptimer.services

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFocusRequest
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
import com.thewizrd.shared_resources.helpers.AppState
import com.thewizrd.shared_resources.sleeptimer.TimerDataModel
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.shared_resources.utils.TimerStringFormatter
import com.thewizrd.simplesleeptimer.*
import com.thewizrd.simplesleeptimer.preferences.Settings
import com.thewizrd.simplesleeptimer.wearable.WearableManager
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.Executors

class TimerService : Service() {
    companion object {
        const val NOT_CHANNEL_ID = "SimpleSleepTimer.timerservice"
        const val NOTIFICATION_ID = 1000

        const val ACTION_START_TIMER = "SimpleSleepTimer.action.START_TIMER"
        internal const val ACTION_UPDATE_TIMER = "SimpleSleepTimer.action.UPDATE_TIMER"
        private const val ACTION_UPDATE_NOTIFICATION = "SimpleSleepTimer.action.UPDATE_NOTIFICATION"
        const val ACTION_CANCEL_TIMER = "SimpleSleepTimer.action.CANCEL_TIMER"
        private const val ACTION_EXPIRE_TIMER = "SimpleSleepTimer.action.EXPIRE_TIMER"
        internal const val ACTION_EXTEND1_TIMER = "SimpleSleepTimer.action.EXTEND1_TIMER"
        internal const val ACTION_EXTEND5_TIMER = "SimpleSleepTimer.action.EXTEND5_TIMER"

        const val EXTRA_TIME_IN_MINS = "SimpleSleepTimer.extra.TIME_IN_MINUTES"
        private const val EXTRA_FORCEFOREGROUND = "SimpleSleepTimer.extra.FORCE_FOREGROUND"

        fun enqueueWork(context: Context, work: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && App.instance.applicationState != AppState.FOREGROUND) {
                context.startForegroundService(work.putExtra(EXTRA_FORCEFOREGROUND, true))
            } else {
                context.startService(work)
            }
        }
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

    private val scope = CoroutineScope(
        SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )

    override fun onCreate() {
        super.onCreate()

        mLocalBroadcastMgr = LocalBroadcastManager.getInstance(this)
        mAlarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        mWearManager = WearableManager(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            initChannel()
            if (App.instance.applicationState != AppState.FOREGROUND) {
                startForegroundIfNeeded()
            }
        }
    }

    private fun startForegroundIfNeeded(forceForeground: Boolean = false) {
        if (!mIsBound || forceForeground) {
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

                    val remainingMinsMs =
                        model.remainingTimeInMs - (model.remainingTimeInMs % DateUtils.MINUTE_IN_MILLIS)
                    if (remainingMinsMs < (TimerModel.MAX_TIME_IN_MINS - 1).times(DateUtils.MINUTE_IN_MILLIS)) {
                        addAction(
                            0,
                            "+" + TimerStringFormatter.getNumberFormattedQuantityString(
                                this@TimerService, R.plurals.minutes_short, 1
                            ),
                            getPendingIntent(ACTION_EXTEND1_TIMER)
                        )
                    }
                    if (remainingMinsMs < (TimerModel.MAX_TIME_IN_MINS - 5).times(DateUtils.MINUTE_IN_MILLIS)) {
                        addAction(
                            0,
                            "+" + TimerStringFormatter.getNumberFormattedQuantityString(
                                this@TimerService, R.plurals.minutes_short, 5
                            ),
                            getPendingIntent(ACTION_EXTEND5_TIMER)
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
        startForegroundIfNeeded(
            intent?.getBooleanExtra(EXTRA_FORCEFOREGROUND, false) ?: false
        )

        when (intent?.action) {
            ACTION_START_TIMER -> {
                if (intent.hasExtra(EXTRA_TIME_IN_MINS)) {
                    val timeInMin = intent.getIntExtra(EXTRA_TIME_IN_MINS, 0)
                    if (timeInMin > 0) {
                        startTimer(timeInMin)
                    }
                }
            }
            ACTION_UPDATE_NOTIFICATION -> {
                updateTimer()
            }
            ACTION_CANCEL_TIMER -> {
                cancelTimer()
            }
            ACTION_EXPIRE_TIMER -> {
                expireTimer()
            }
            ACTION_UPDATE_TIMER -> {
                updateExpireIntent()
                updateTimer()
            }
            ACTION_EXTEND1_TIMER -> {
                model.extend1Min()
                updateExpireIntent()
                updateTimer()
            }
            ACTION_EXTEND5_TIMER -> {
                model.extend5Min()
                updateExpireIntent()
                updateTimer()
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
        sendPublicTimerUpdate()
    }

    private fun expireTimer() {
        pauseMusicAction()
        cancelTimer()
    }

    private fun cancelTimer() {
        cancelUpdateIntent()
        cancelExpireIntent()
        if (model.isRunning) {
            model.stopTimer()
            sendTimerCancelled()
        }
        stopForeground(true)
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun sendPublicTimerUpdate() {
        mWearManager.sendSleepTimerUpdate(model.toModel())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            QSTileService.requestListeningState(this)
        }
    }

    private fun sendTimerStarted() {
        mLocalBroadcastMgr.sendBroadcast(
            Intent(ACTION_START_TIMER)
        )
        mWearManager.sendSleepTimerStart(model.toModel())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            QSTileService.requestListeningState(this)
        }
    }

    private fun sendTimerCancelled() {
        mLocalBroadcastMgr.sendBroadcast(
            Intent(ACTION_CANCEL_TIMER)
        )
        mWearManager.sendSleepCancelled()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            QSTileService.requestListeningState(this)
        }
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
        val intent = Intent(context.applicationContext, SleepTimerActivity::class.java)
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
            .setAction(ACTION_UPDATE_NOTIFICATION)

        return PendingIntent.getService(this, 0, i, flags)
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

        return PendingIntent.getService(this, 0, i, flags)
    }

    private fun updateExpireIntent() {
        cancelExpireIntent()
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

    private fun getPendingIntent(action: String): PendingIntent {
        val flags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT

        val i = Intent(this, TimerService::class.java)
            .setAction(action)

        return PendingIntent.getService(this, 0, i, flags)
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

        // Use AudioFocus as a fallback
        if (audioMan.isMusicActive) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .build()
                audioMan.requestAudioFocus(request)
                audioMan.abandonAudioFocusRequest(request)
            } else {
                audioMan.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
                audioMan.abandonAudioFocus(null)
            }
        }
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
            scope.launch {
                this@TimerService.cancelTimer()
            }
        }

        fun startTimer(timeInMin: Int) {
            scope.launch {
                this@TimerService.startTimer(timeInMin)
            }
        }

        fun updateTimer() {
            scope.launch {
                this@TimerService.updateTimer()
            }
        }

        fun extend1MinTimer() {
            scope.launch {
                model.extend1Min()
                this@TimerService.updateExpireIntent()
                this@TimerService.updateTimer()
            }
        }

        fun extend5MinTimer() {
            scope.launch {
                model.extend5Min()
                this@TimerService.updateExpireIntent()
                this@TimerService.updateTimer()
            }
        }
    }

    override fun onDestroy() {
        cancelTimer()
        mWearManager.unregister()
        scope.cancel()
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
        startForegroundIfNeeded(true)

        return true // Allow re-binding
    }
}