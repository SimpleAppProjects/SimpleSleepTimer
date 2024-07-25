package com.thewizrd.shared_resources.services

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.text.format.DateUtils
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.CallSuper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.thewizrd.shared_resources.R
import com.thewizrd.shared_resources.SimpleLibrary
import com.thewizrd.shared_resources.helpers.AppState
import com.thewizrd.shared_resources.helpers.toImmutableCompatFlag
import com.thewizrd.shared_resources.sleeptimer.TimerDataModel
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Timer
import java.util.concurrent.Executors
import kotlin.concurrent.schedule

abstract class BaseTimerService : Service() {
    companion object {
        const val ACTION_START_TIMER = "SimpleSleepTimer.action.START_TIMER"
        const val ACTION_UPDATE_TIMER = "SimpleSleepTimer.action.UPDATE_TIMER"
        const val ACTION_UPDATE_NOTIFICATION = "SimpleSleepTimer.action.UPDATE_NOTIFICATION"
        const val ACTION_CANCEL_TIMER = "SimpleSleepTimer.action.CANCEL_TIMER"
        const val ACTION_EXPIRE_TIMER = "SimpleSleepTimer.action.EXPIRE_TIMER"
        const val ACTION_EXTEND1_TIMER = "SimpleSleepTimer.action.EXTEND1_TIMER"
        const val ACTION_EXTEND5_TIMER = "SimpleSleepTimer.action.EXTEND5_TIMER"

        const val EXTRA_TIME_IN_MINS = "SimpleSleepTimer.extra.TIME_IN_MINUTES"
        private const val EXTRA_FORCEFOREGROUND = "SimpleSleepTimer.extra.FORCE_FOREGROUND"

        fun enqueueWork(context: Context, work: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && SimpleLibrary.instance.app.applicationState != AppState.FOREGROUND) {
                context.startForegroundService(work.putExtra(EXTRA_FORCEFOREGROUND, true))
            } else {
                context.startService(work)
            }
        }

        @RequiresApi(Build.VERSION_CODES.S)
        fun checkExactAlarmsPermission(context: Context): Boolean {
            val alarmMgr =
                ContextCompat.getSystemService(context.applicationContext, AlarmManager::class.java)
            return alarmMgr?.canScheduleExactAlarms() ?: false
        }
    }

    private lateinit var mLocalBroadcastMgr: LocalBroadcastManager
    private lateinit var mAlarmManager: AlarmManager
    private var timerFallback: Timer? = null

    private var mForegroundNotification: Notification? = null
    private var mIsBound: Boolean = false

    // Binder given to clients
    private val binder = LocalBinder()

    // Timer
    private val model = TimerDataModel.getDataModel()

    private val scope = CoroutineScope(
        SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )

    protected abstract val notificationId: Int
    protected abstract val notificationChannelId: String

    protected fun getTimerModel(): TimerModel {
        return model.toModel()
    }

    @CallSuper
    override fun onCreate() {
        super.onCreate()

        mLocalBroadcastMgr = LocalBroadcastManager.getInstance(this)
        mAlarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            initChannel()
            if (SimpleLibrary.instance.app.applicationState != AppState.FOREGROUND) {
                startForegroundIfNeeded()
            }
        }
    }

    private fun startForegroundIfNeeded(forceForeground: Boolean = false) {
        if (!mIsBound || forceForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    notificationId,
                    getForegroundNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
                )
            } else {
                startForeground(notificationId, getForegroundNotification())
            }
            updateNotification()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    protected fun initChannel() {
        val context = this.applicationContext
        val mNotifyMgr =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        var mChannel = mNotifyMgr.getNotificationChannel(notificationChannelId)
        val notChannelName = context.getString(R.string.not_channel_name_timer)

        if (mChannel == null) {
            mChannel = NotificationChannel(
                notificationChannelId,
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
                NotificationCompat.Builder(this, notificationChannelId)
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

    @SuppressLint("MissingPermission")
    private fun updateNotification() {
        val remainingTime = model.remainingTimeInMs

        mForegroundNotification = updateTimerNotification(model.toModel())

        NotificationManagerCompat.from(this)
            .notify(notificationId, mForegroundNotification!!)

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

    protected abstract fun updateTimerNotification(model: TimerModel): Notification

    @CallSuper
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

    protected fun startTimer(timeInMin: Int) {
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
            NotificationManagerCompat.from(this).cancel(notificationId)
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
        timerFallback?.cancel()
        if (model.isRunning) {
            model.stopTimer()
            sendTimerCancelled()
        }
        stopForeground(true)
        NotificationManagerCompat.from(this).cancel(notificationId)
        stopSelf()
    }

    protected abstract fun sendPublicTimerUpdate()

    @CallSuper
    protected open fun sendTimerStarted() {
        mLocalBroadcastMgr.sendBroadcast(
            Intent(ACTION_START_TIMER)
        )
    }

    @CallSuper
    protected open fun sendTimerCancelled() {
        mLocalBroadcastMgr.sendBroadcast(
            Intent(ACTION_CANCEL_TIMER)
        )
    }

    protected fun getCancelIntent(context: Context): PendingIntent {
        val intent = Intent(context.applicationContext, this::class.java)
            .setAction(ACTION_CANCEL_TIMER)

        return PendingIntent.getService(
            context.applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT.toImmutableCompatFlag()
        )
    }

    protected fun getClickIntent(context: Context): PendingIntent {
        val intent = Intent(context.applicationContext, getOnClickActivityClass())
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

        return PendingIntent.getActivity(
            context.applicationContext,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT.toImmutableCompatFlag()
        )
    }

    protected abstract fun getOnClickActivityClass(): Class<out Activity>

    private fun getUpdateIntent(): PendingIntent {
        return getUpdateIntent(false)!!
    }

    private fun getUpdateIntent(cancel: Boolean = false): PendingIntent? {
        val flags = if (cancel) {
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_NO_CREATE
        } else {
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT
        }

        val i = Intent(this, this::class.java)
            .setAction(ACTION_UPDATE_NOTIFICATION)

        return PendingIntent.getService(this, 0, i, flags.toImmutableCompatFlag())
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

        val i = Intent(this, this::class.java)
            .setAction(ACTION_EXPIRE_TIMER)

        return PendingIntent.getService(this, 0, i, flags.toImmutableCompatFlag())
    }

    private fun getTimerFallBack(): Timer {
        if (timerFallback == null) {
            timerFallback = Timer("sleeptimer")
        }

        return timerFallback!!
    }

    private fun updateExpireIntent() {
        cancelExpireIntent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !checkExactAlarmsPermission(this)) {
            timerFallback?.cancel()
            timerFallback = getTimerFallBack()
            timerFallback!!.schedule(Date(model.endTimeInMs)) {
                expireTimer()
            }
        } else {
            scheduleExpirePendingIntent(getExpireIntent())
        }
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
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mAlarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    rtcExpireTimeInMs,
                    pi
                )
            } else {
                mAlarmManager.setExact(AlarmManager.RTC_WAKEUP, rtcExpireTimeInMs, pi)
            }
        }.onFailure {
            Log.e("BaseTimerService", "Error", it)
        }
    }

    protected fun getExtend1MinPendingIntent(): PendingIntent {
        return getPendingIntent(ACTION_EXTEND1_TIMER)
    }

    protected fun getExtend5MinPendingIntent(): PendingIntent {
        return getPendingIntent(ACTION_EXTEND5_TIMER)
    }

    private fun getPendingIntent(action: String): PendingIntent {
        val flags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT

        val i = Intent(this, this::class.java)
            .setAction(action)

        return PendingIntent.getService(this, 0, i, flags.toImmutableCompatFlag())
    }

    private fun pauseMusicAction() {
        // Check if audio player preference exists
        pauseSelectedMediaPlayer()

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

    protected abstract fun pauseSelectedMediaPlayer()

    final override fun onBind(intent: Intent?): IBinder {
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
                this@BaseTimerService.cancelTimer()
            }
        }

        fun startTimer(timeInMin: Int) {
            scope.launch {
                this@BaseTimerService.startTimer(timeInMin)
            }
        }

        fun updateTimer() {
            scope.launch {
                this@BaseTimerService.updateTimer()
            }
        }

        fun extend1MinTimer() {
            scope.launch {
                model.extend1Min()
                this@BaseTimerService.updateExpireIntent()
                this@BaseTimerService.updateTimer()
            }
        }

        fun extend5MinTimer() {
            scope.launch {
                model.extend5Min()
                this@BaseTimerService.updateExpireIntent()
                this@BaseTimerService.updateTimer()
            }
        }
    }

    @CallSuper
    override fun onDestroy() {
        cancelTimer()
        timerFallback?.purge()
        scope.cancel()
        super.onDestroy()
        stopForeground(true)
    }

    final override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        mIsBound = true

        // Background restrictions don't apply to bound services
        // We can remove the notification now
        stopForeground(true)
    }

    final override fun onUnbind(intent: Intent?): Boolean {
        // Since service is unbound, background restrictions now apply
        // Start foreground to notify system
        mIsBound = false
        startForegroundIfNeeded(true)

        return true // Allow re-binding
    }
}