package com.thewizrd.simplesleeptimer.wearable

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.stringToBytes
import com.thewizrd.simplesleeptimer.LaunchActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WearableDataListenerService : WearableListenerService() {
    companion object {
        private const val TAG = "WearableListenerService"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == WearableHelper.StartActivityPath) {
            val startIntent = Intent(this, LaunchActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            this.startActivity(startIntent)
        } else if (messageEvent.path == WearableHelper.BtDiscoverPath) {
            this.startActivity(
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 30)
            )

            GlobalScope.launch(Dispatchers.Default) {
                sendMessage(
                    messageEvent.sourceNodeId,
                    messageEvent.path,
                    Build.MODEL.stringToBytes()
                )
            }
        }
    }

    protected suspend fun sendMessage(nodeID: String, path: String, data: ByteArray?) {
        try {
            Wearable.getMessageClient(this@WearableDataListenerService)
                .sendMessage(nodeID, path, data).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
        }
    }
}