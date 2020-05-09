package com.thewizrd.simplesleeptimer.services

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.view.KeyEvent
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.util.ObjectsCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.thewizrd.simplesleeptimer.App
import com.thewizrd.simplesleeptimer.AppState
import com.thewizrd.simplesleeptimer.MainActivity
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.preferences.Settings
import com.thewizrd.simplesleeptimer.utils.StringUtils.Companion.isNullOrWhitespace
import java.util.*

class TimerService : Service() {

    companion object {
        const val NOT_CHANNEL_ID = "SimpleSleepTimer.timerservice"

        const val NOTIFICATION_TAG = "SimpleSleepTimer.timernotification"
        const val NOTIFICATION_ID = 1000

        const val ACTION_START_TIMER = "SimpleSleepTimer.action.START_TIMER"
        const val ACTION_CANCEL_TIMER = "SimpleSleepTimer.action.CANCEL_TIMER"
        const val EXTRA_TIME_IN_MINS = "SimpleSleepTimer.extra.TIME_IN_MINUTES"

        const val ACTION_TIME_UPDATED = "SimpleSleepTimer.action.TIME_UPDATED"
        const val EXTRA_START_TIME_IN_MS = "SimpleSleepTimer.extra.START_TIME_IN_MS"
        const val EXTRA_TIME_IN_MS = "SimpleSleepTimer.extra.TIME_IN_MS"
    }

    private var timer: CountDownTimer? = null
    private var mIsRunning: Boolean = false
    private var mForegroundNotification: Notification? = null
    private var mIsBound: Boolean = false

    // Binder given to clients
    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            initChannel()
            startForegroundIfNeeded()
        }
    }

    private fun startForegroundIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appState = App.getInstance().getAppState()
            if (!mIsBound && appState != AppState.FOREGROUND) {
                startForeground(NOTIFICATION_ID, getForegroundNotification())
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initChannel() {
        val context = this.applicationContext
        val mNotifyMgr =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        var mChannel = mNotifyMgr.getNotificationChannel(NOT_CHANNEL_ID)

        if (mChannel == null) {
            val notChannelName = context.getString(R.string.not_channel_name_timer)

            mChannel = NotificationChannel(
                NOT_CHANNEL_ID,
                notChannelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                // Configure the notification channel
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            mNotifyMgr.createNotificationChannel(mChannel)
        }
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
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startTimer(timeInMin: Int) {
        timer?.cancel()
        timer = object : CountDownTimer((timeInMin * 60000).toLong(), 1) {
            private var lastMillisUntilFinished: Long = (timeInMin * 60000).toLong()

            override fun onTick(millisUntilFinished: Long) {
                mIsRunning = true
                val hours = millisUntilFinished / 3600000L
                val mins = millisUntilFinished % 3600000L / 60000L
                val secs = (millisUntilFinished / 1000) % 60

                LocalBroadcastManager.getInstance(this@TimerService)
                    .sendBroadcast(
                        Intent(ACTION_TIME_UPDATED)
                            .putExtra(EXTRA_START_TIME_IN_MS, (timeInMin * 60000).toLong())
                            .putExtra(EXTRA_TIME_IN_MS, millisUntilFinished)
                    )

                // Only update notification every second (or so)
                if ((lastMillisUntilFinished - millisUntilFinished) > 1000) {
                    // Send public broadcast every second
                    this@TimerService.sendBroadcast(
                        Intent(ACTION_TIME_UPDATED)
                            .putExtra(EXTRA_START_TIME_IN_MS, (timeInMin * 60000).toLong())
                            .putExtra(EXTRA_TIME_IN_MS, millisUntilFinished),
                        "com.thewizrd.simplesleeptimer.permission.SLEEP_TIMER"
                    )

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

                    lastMillisUntilFinished = millisUntilFinished
                }
            }

            override fun onFinish() {
                pauseMusicAction()
                cancelTimer()
            }
        }
        this.sendBroadcast(
            Intent(ACTION_START_TIMER),
            "com.thewizrd.simplesleeptimer.permission.SLEEP_TIMER"
        )
        timer?.start()
        mIsRunning = true
    }

    private fun cancelTimer() {
        if (mIsRunning) {
            this.sendBroadcast(
                Intent(ACTION_CANCEL_TIMER),
                "com.thewizrd.simplesleeptimer.permission.SLEEP_TIMER"
            )
            mIsRunning = false
        }
        timer?.cancel()
        NotificationManagerCompat.from(this@TimerService).cancel(NOTIFICATION_ID)
        stopSelf()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true)
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
        val intent = Intent(context.applicationContext, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

        return PendingIntent.getActivity(
            context.applicationContext,
            0,
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
                val pkgName = data[0];
                val activityName = data[1];

                if (!String.isNullOrWhitespace(pkgName) && !String.isNullOrWhitespace(activityName)) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val audioMan = this.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
            audioMan.dispatchMediaKeyEvent(event)
        } else {
            val intent = Intent("com.android.music.musicservicecommand")
            intent.putExtra("command", "pause")
            this.sendBroadcast(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Background restrictions don't apply to bound services
        // We can remove the notification now
        mIsBound = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
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
        super.onDestroy()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            stopForeground(true)
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        mIsBound = true

        // Background restrictions don't apply to bound services
        // We can remove the notification now
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            stopForeground(true)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // Since service is unbound, background restrictions now apply
        // Start foreground to notify system
        mIsBound = false
        startForegroundIfNeeded()

        return super.onUnbind(intent)
    }
}