package com.thewizrd.simplesleeptimer.wearable

import android.content.Context
import android.util.Log
import androidx.core.util.Pair
import androidx.work.*
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.simplesleeptimer.model.TimerDataModel

class WearableWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    companion object {
        private const val TAG = "WearableWorker"

        // Actions
        private const val KEY_ACTION = "action"
        private const val KEY_DATA = "data"
        private const val KEY_NODEID = "node_id"

        fun sendSupportedMusicPlayers(context: Context) {
            startWork(
                context,
                Data.Builder()
                    .putString(KEY_ACTION, WearableHelper.MusicPlayersPath)
                    .build()
            )
        }

        fun startMusicPlayer(context: Context, nodeID: String, jsonData: String) {
            startWork(
                context, Data.Builder()
                    .putString(KEY_ACTION, WearableHelper.OpenMusicPlayerPath)
                    .putString(KEY_NODEID, nodeID)
                    .putString(KEY_DATA, jsonData)
                    .build()
            )
        }

        fun sendSleepTimerStatus(context: Context) {
            startWork(context, SleepTimerHelper.SleepTimerStatusPath)
        }

        fun sendSelectedAudioPlayer(context: Context) {
            startWork(context, SleepTimerHelper.SleepTimerAudioPlayerPath)
        }

        private fun startWork(context: Context, intentAction: String) {
            startWork(context, Data.Builder().putString(KEY_ACTION, intentAction).build())
        }

        private fun startWork(context: Context, inputData: Data?) {
            Log.i(TAG, "Requesting to start work")
            val updateRequest = OneTimeWorkRequest.Builder(WearableWorker::class.java)
            if (inputData != null) {
                updateRequest.setInputData(inputData)
            }
            WorkManager.getInstance(context.applicationContext).enqueue(updateRequest.build())
            Log.i(TAG, "One-time work enqueued")
        }
    }

    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION) ?: return Result.success()
        val mWearMgr = WearableManager(applicationContext)

        when (action) {
            WearableHelper.MusicPlayersPath -> {
                mWearMgr.sendSupportedMusicPlayers()
            }
            WearableHelper.OpenMusicPlayerPath -> {
                val nodeID = inputData.getString(KEY_NODEID)
                val jsonData = inputData.getString(KEY_DATA)
                val pair = JSONParser.deserializer(jsonData, Pair::class.java)
                val pkgName = pair?.first.toString()
                val activityName = pair?.second.toString()
                mWearMgr.startMusicPlayer(nodeID, pkgName, activityName)
            }
            SleepTimerHelper.SleepTimerStatusPath -> {
                mWearMgr.sendSleepTimerUpdate(null, TimerDataModel.getDataModel().toModel())
            }
            SleepTimerHelper.SleepTimerAudioPlayerPath -> {
                mWearMgr.sendSelectedAudioPlayer()
            }
        }

        return Result.success()
    }
}