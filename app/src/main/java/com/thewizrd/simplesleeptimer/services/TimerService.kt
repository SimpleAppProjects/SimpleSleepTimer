package com.thewizrd.simplesleeptimer.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
import com.thewizrd.simplesleeptimer.MainActivity
import com.thewizrd.simplesleeptimer.R
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

    // Binder given to clients
    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            initChannel()
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
            override fun onTick(millisUntilFinished: Long) {
                mIsRunning = true
                val hours = millisUntilFinished / 3600000L
                val mins = millisUntilFinished % 3600000L / 60000L
                val secs = (millisUntilFinished / 1000) % 60

                val mNotification = NotificationCompat.Builder(this@TimerService, NOT_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_hourglass_empty)
                    .setContentTitle(getString(R.string.title_sleeptimer))
                    .setContentText(String.format(Locale.ROOT, "%02d:%02d:%02d", hours, mins, secs))
                    .setColor(ContextCompat.getColor(this@TimerService, R.color.colorPrimary))
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setSound(null)
                    .addAction(
                        NotificationCompat.Action.Builder(
                            0,
                            getString(android.R.string.cancel),
                            getCancelIntent(this@TimerService)
                        ).build()
                    )
                    .setContentIntent(getClickIntent(this@TimerService))
                    .build()

                NotificationManagerCompat.from(this@TimerService)
                    .notify(NOTIFICATION_TAG, NOTIFICATION_ID, mNotification)

                this@TimerService.sendBroadcast(
                    Intent(ACTION_TIME_UPDATED)
                        .putExtra(EXTRA_START_TIME_IN_MS, (timeInMin * 60000).toLong())
                        .putExtra(EXTRA_TIME_IN_MS, millisUntilFinished)
                )
            }

            override fun onFinish() {
                mIsRunning = false
                pauseMusicAction()
                cancelTimer()
            }
        }
        this.sendBroadcast(Intent(ACTION_START_TIMER))
        timer?.start()
        mIsRunning = true
    }

    private fun cancelTimer() {
        if (mIsRunning) {
            this.sendBroadcast(Intent(ACTION_CANCEL_TIMER))
            mIsRunning = false
        }
        timer?.cancel()
        NotificationManagerCompat.from(this@TimerService).cancel(NOTIFICATION_TAG, NOTIFICATION_ID)
        stopSelf()
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
    }
}