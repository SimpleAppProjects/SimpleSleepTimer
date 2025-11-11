package com.thewizrd.simplesleeptimer.wearable

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.utils.Logger

class WearableWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    companion object {
        private const val TAG = "WearableWorker"

        // Actions
        private const val KEY_ACTION = "action"
        private const val KEY_DATA = "data"
        private const val KEY_NODEID = "node_id"

        fun enqueueAction(context: Context, intentAction: String) {
            when (intentAction) {
                SleepTimerHelper.SleepTimerAudioPlayerPath -> {
                    startWork(context, intentAction)
                }
            }
        }

        fun sendSelectedAudioPlayer(context: Context) {
            startWork(context, SleepTimerHelper.SleepTimerAudioPlayerPath)
        }

        private fun startWork(context: Context, intentAction: String) {
            startWork(context, Data.Builder().putString(KEY_ACTION, intentAction).build())
        }

        private fun startWork(context: Context, inputData: Data?) {
            Logger.info(TAG, "Requesting to start work")
            val updateRequest = OneTimeWorkRequest.Builder(WearableWorker::class.java)
            if (inputData != null) {
                updateRequest.setInputData(inputData)
            }
            WorkManager.getInstance(context.applicationContext).enqueue(updateRequest.build())
            Logger.info(TAG, "One-time work enqueued")
        }
    }

    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION) ?: return Result.success()
        val mWearMgr = WearableManager(applicationContext)

        when (action) {
            SleepTimerHelper.SleepTimerAudioPlayerPath -> {
                mWearMgr.sendSelectedAudioPlayer(null)
            }
        }

        return Result.success()
    }
}