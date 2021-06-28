package com.thewizrd.simplesleeptimer.services

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.*
import android.view.KeyEvent
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.util.ObjectsCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.thewizrd.simplesleeptimer.*
import com.thewizrd.simplesleeptimer.preferences.Settings
import com.thewizrd.simplesleeptimer.wearable.WearableManager
import java.util.*

class TimerService : Service() {
    companion object {
        const val NOT_CHANNEL_ID = "SimpleSleepTimer.timerservice"
        const val NOTIFICATION_ID = 1000

        const val ACTION_START_TIMER = "SimpleSleepTimer.action.START_TIMER"
        const val ACTION_CANCEL_TIMER = "SimpleSleepTimer.action.CANCEL_TIMER"
        const val EXTRA_TIME_IN_MINS = "SimpleSleepTimer.extra.TIME_IN_MINUTES"

        const val ACTION_TIME_UPDATED = "SimpleSleepTimer.action.TIME_UPDATED"
        const val EXTRA_START_TIME_IN_MS = "SimpleSleepTimer.extra.START_TIME_IN_MS"
        const val EXTRA_TIME_IN_MS = "SimpleSleepTimer.extra.TIME_IN_MS"
    }

    private lateinit var mLocalBroadcastMgr: LocalBroadcastManager

    private var timer: CountDownTimer? = null
    private var mIsRunning: Boolean = false
    private var mForegroundNotification: Notification? = null
    private var mIsBound: Boolean = false

    // Wearable
    private lateinit var mWearManager: WearableManager

    // Binder given to clients
    private val binder = LocalBinder()

    private val timerThread = HandlerThread("timer")
    private lateinit var timerHandler: Handler

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            initChannel()
        }
        startForegroundIfNeeded()

        mLocalBroadcastMgr = LocalBroadcastManager.getInstance(this)
        mWearManager = WearableManager(this)
        timerThread.start()
        timerHandler = Handler(timerThread.looper)
    }

    private fun startForegroundIfNeeded() {
        if (!mIsBound) {
            startForeground(NOTIFICATION_ID, getForegroundNotification())
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
                    .setColor(
                        ContextCompat.getColor(
                            this@TimerService,
                            R.color.colorPrimary
                        )
                    )
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setSound(null)
                    .addAction(
                        0,
                        getString(android.R.string.cancel),
                        getCancelIntent(this@TimerService)
                    )
                    .setContentIntent(getClickIntent(this@TimerService))
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
        }

        return mForegroundNotification!!
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundIfNeeded()

        if (ObjectsCompat.equals(intent?.action, ACTION_START_TIMER)) {
            if (intent?.hasExtra(EXTRA_TIME_IN_MINS) == true) {
                val timeInMin = intent.getIntExtra(EXTRA_TIME_IN_MINS, 0)
                startTimer(timeInMin)
            }
        } else if (ObjectsCompat.equals(intent?.action, ACTION_CANCEL_TIMER)) {
            cancelTimer()
        }

        return START_STICKY
    }

    private fun startTimer(timeInMin: Int) {
        timerHandler.post {
            timer?.cancel()
            timer = object : CountDownTimer((timeInMin * 60000).toLong(), 1) {
                private var lastMillisUntilFinished: Long = (timeInMin * 60000).toLong()

                override fun onTick(millisUntilFinished: Long) {
                    mIsRunning = true
                    val hours = millisUntilFinished / 3600000L
                    val mins = millisUntilFinished % 3600000L / 60000L
                    val secs = (millisUntilFinished / 1000) % 60

                    val startTimeInMs = (timeInMin * 60000).toLong()

                    mLocalBroadcastMgr.sendBroadcast(
                        Intent(ACTION_TIME_UPDATED)
                            .putExtra(EXTRA_START_TIME_IN_MS, startTimeInMs)
                            .putExtra(EXTRA_TIME_IN_MS, millisUntilFinished)
                    )

                    // Only update notification every second (or so)
                    if ((lastMillisUntilFinished - millisUntilFinished) > 1000) {
                        // Send public broadcast every second
                        sendPublicTimerUpdate(startTimeInMs, millisUntilFinished)

                        if (!mIsBound) {
                            mForegroundNotification =
                                NotificationCompat.Builder(this@TimerService, NOT_CHANNEL_ID)
                                    .setSmallIcon(R.drawable.ic_hourglass_empty)
                                    .setContentTitle(getString(R.string.title_sleeptimer))
                                    .setContentText(
                                        String.format(
                                            Locale.ROOT,
                                            "%02d:%02d:%02d",
                                            hours,
                                            mins,
                                            secs
                                        )
                                    )
                                    .setColor(
                                        ContextCompat.getColor(
                                            this@TimerService,
                                            R.color.colorPrimary
                                        )
                                    )
                                    .setOngoing(true)
                                    .setOnlyAlertOnce(true)
                                    .setSound(null)
                                    .addAction(
                                        0,
                                        getString(android.R.string.cancel),
                                        getCancelIntent(this@TimerService)
                                    )
                                    .setContentIntent(getClickIntent(this@TimerService))
                                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                                    .build()

                            NotificationManagerCompat.from(this@TimerService)
                                .notify(NOTIFICATION_ID, mForegroundNotification!!)
                        } else {
                            NotificationManagerCompat.from(this@TimerService)
                                .cancel(NOTIFICATION_ID)
                        }

                        lastMillisUntilFinished = millisUntilFinished
                    }
                }

                override fun onFinish() {
                    pauseMusicAction()
                    cancelTimer()
                }
            }
            sendTimerStarted()
            timer?.start()
            mIsRunning = true
        }
    }

    private fun cancelTimer() {
        if (mIsRunning) {
            sendTimerCancelled()
            mIsRunning = false
        }
        timer?.cancel()
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
        fun isRunning(): Boolean = mIsRunning

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
        timerHandler.removeCallbacksAndMessages(null)
        timerThread.quit()
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