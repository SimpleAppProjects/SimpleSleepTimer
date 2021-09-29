package com.thewizrd.simplesleeptimer.services

import android.content.*
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.RequiresApi
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.devadvance.circularseekbar.CircularSeekBar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.thewizrd.shared_resources.services.BaseTimerService
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.sleeptimer.TimerDataModel
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.shared_resources.utils.TimerStringFormatter
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.databinding.DialogTimerStartBinding

@RequiresApi(Build.VERSION_CODES.N)
class QSTileService : TileService() {
    companion object {
        @RequiresApi(Build.VERSION_CODES.N)
        fun requestListeningState(context: Context) {
            requestListeningState(
                context.applicationContext,
                ComponentName(context.applicationContext, QSTileService::class.java)
            )
        }
    }

    private lateinit var mLocalBroadcastMgr: LocalBroadcastManager
    private lateinit var mBroadcastReceiver: BroadcastReceiver
    private lateinit var mIntentFilter: IntentFilter

    override fun onCreate() {
        super.onCreate()
        mLocalBroadcastMgr = LocalBroadcastManager.getInstance(this)
        mIntentFilter = IntentFilter().apply {
            addAction(BaseTimerService.ACTION_START_TIMER)
            addAction(BaseTimerService.ACTION_CANCEL_TIMER)
        }
        mBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                when (intent?.action) {
                    BaseTimerService.ACTION_START_TIMER, BaseTimerService.ACTION_CANCEL_TIMER -> {
                        updateState()
                    }
                }
            }
        }
        mLocalBroadcastMgr.registerReceiver(mBroadcastReceiver, mIntentFilter)
    }

    override fun onDestroy() {
        mLocalBroadcastMgr.unregisterReceiver(mBroadcastReceiver)
        super.onDestroy()
    }

    override fun onStartListening() {
        super.onStartListening()
        // Keep tile state up-to-date
        updateState()
    }

    override fun onStopListening() {
        // Stop UI updates
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (!TimerDataModel.getDataModel().isRunning) {
            buildAlertDialog()
        } else {
            val stopTimerIntent = Intent(this, TimerService::class.java)
                .setAction(SleepTimerHelper.ACTION_CANCEL_TIMER)
            BaseTimerService.enqueueWork(this, stopTimerIntent)
        }
        updateState()
    }

    private fun updateState() {
        val tile = qsTile
        val model = TimerDataModel.getDataModel()
        if (model.isRunning) {
            tile.state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(R.string.label_stop)
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(R.string.label_start)
            }
        }
        tile.updateTile()
    }

    private fun buildAlertDialog() {
        // Open dialog to setup timer
        val model = TimerModel()

        val dialog = MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogStyle).apply {
            setView(createDialogView(context, model))
            setCancelable(true)
            setTitle(R.string.title_sleeptimer)
            setPositiveButton(R.string.label_start) { _, _ ->
                if (model.timerLengthInMins > 0) {
                    val startTimerIntent = Intent(this@QSTileService, TimerService::class.java)
                        .setAction(SleepTimerHelper.ACTION_START_TIMER)
                        .putExtra(SleepTimerHelper.EXTRA_TIME_IN_MINS, model.timerLengthInMins)
                    BaseTimerService.enqueueWork(this@QSTileService, startTimerIntent)
                    updateState()
                }
            }
            setNegativeButton(android.R.string.cancel, null)
        }.create()

        showDialog(dialog)
    }

    private fun createDialogView(context: Context, model: TimerModel): View {
        val binding = DialogTimerStartBinding.inflate(LayoutInflater.from(context))

        binding.timerProgressScroller.setOnSeekBarChangeListener(object :
            CircularSeekBar.OnCircularSeekBarChangeListener {
            override fun onProgressChanged(
                circularSeekBar: CircularSeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                setProgressText(progress)
                model.timerLengthInMins = progress
            }

            override fun onStartTrackingTouch(seekBar: CircularSeekBar?) {}
            override fun onStopTrackingTouch(seekBar: CircularSeekBar?) {}

            private fun setProgressText(progress: Int) {
                val hours = progress / 60
                val minutes = progress - (hours * 60)

                if (hours > 0) {
                    binding.progressText.text =
                        context.getString(
                            com.thewizrd.shared_resources.R.string.timer_progress_hours_minutes,
                            hours,
                            minutes
                        )
                } else {
                    binding.progressText.text =
                        TimerStringFormatter.getNumberFormattedQuantityString(
                            context, com.thewizrd.shared_resources.R.plurals.minutes_short, minutes
                        )
                }
            }
        })

        binding.timerProgressScroller.max = TimerModel.MAX_TIME_IN_MINS
        binding.timerProgressScroller.progress = model.timerLengthInMins

        return binding.root
    }
}