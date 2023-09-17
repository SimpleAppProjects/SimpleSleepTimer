package com.thewizrd.simplesleeptimer.wearable.tiles

import android.content.Context
import android.text.format.DateUtils
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableStatusCodes
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.shared_resources.utils.intToBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

class TimerTileMessenger(private val context: Context) :
    CapabilityClient.OnCapabilityChangedListener, MessageClient.OnMessageReceivedListener {
    companion object {
        private const val TAG = "TimerTileMessenger"
    }

    @Volatile
    private var mPhoneNodeWithApp: Node? = null
    private var mConnectionStatus = WearConnectionStatus.DISCONNECTED
    private var timerModel = TimerModel()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun register() {
        Wearable.getCapabilityClient(context)
            .addListener(this, WearableHelper.CAPABILITY_PHONE_APP)

        Wearable.getMessageClient(context)
            .addListener(this)
    }

    fun unregister() {
        Wearable.getCapabilityClient(context)
            .removeListener(this, WearableHelper.CAPABILITY_PHONE_APP)

        Wearable.getMessageClient(context)
            .removeListener(this)

        scope.cancel()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "message received - path: ${messageEvent.path}")

        scope.launch {
            when (messageEvent.path) {
                SleepTimerHelper.SleepTimerStatusPath, SleepTimerHelper.SleepTimerStartPath -> {
                    val data = JSONParser.deserializer(
                        messageEvent.data.bytesToString(),
                        TimerModel::class.java
                    )

                    data?.let {
                        // Add a second for latency
                        it.endTimeInMs = it.endTimeInMs + DateUtils.SECOND_IN_MILLIS
                        timerModel.updateModel(it)
                    } ?: return@launch

                    // something
                }

                SleepTimerHelper.SleepTimerStopPath -> {
                    timerModel.stopTimer()
                }
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        scope.launch {
            val connectedNodes = getConnectedNodes()
            mPhoneNodeWithApp = pickBestNodeId(capabilityInfo.nodes)

            if (mPhoneNodeWithApp == null) {
                /*
                 * If a device is disconnected from the wear network, capable nodes are empty
                 *
                 * No capable nodes can mean the app is not installed on the remote device or the
                 * device is disconnected.
                 *
                 * Verify if we're connected to any nodes; if not, we're truly disconnected
                 */
                mConnectionStatus = if (connectedNodes.isEmpty()) {
                    WearConnectionStatus.DISCONNECTED
                } else {
                    WearConnectionStatus.APPNOTINSTALLED
                }
            } else {
                if (mPhoneNodeWithApp!!.isNearby && connectedNodes.any { it.id == mPhoneNodeWithApp!!.id }) {
                    mConnectionStatus = WearConnectionStatus.CONNECTED
                } else {
                    try {
                        sendPing(mPhoneNodeWithApp!!.id)
                        mConnectionStatus = WearConnectionStatus.CONNECTED
                    } catch (e: ApiException) {
                        if (e.statusCode == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED) {
                            mConnectionStatus = WearConnectionStatus.DISCONNECTED
                        } else {
                            Log.e(TAG, "Error", e)
                        }
                    }
                }
            }
        }
    }

    suspend fun checkConnectionStatus() {
        val connectedNodes = getConnectedNodes()
        mPhoneNodeWithApp = checkIfPhoneHasApp()

        if (mPhoneNodeWithApp == null) {
            /*
             * If a device is disconnected from the wear network, capable nodes are empty
             *
             * No capable nodes can mean the app is not installed on the remote device or the
             * device is disconnected.
             *
             * Verify if we're connected to any nodes; if not, we're truly disconnected
             */
            mConnectionStatus = if (connectedNodes.isEmpty()) {
                WearConnectionStatus.DISCONNECTED
            } else {
                WearConnectionStatus.APPNOTINSTALLED
            }
        } else {
            if (mPhoneNodeWithApp!!.isNearby && connectedNodes.any { it.id == mPhoneNodeWithApp!!.id }) {
                mConnectionStatus = WearConnectionStatus.CONNECTED
            } else {
                try {
                    sendPing(mPhoneNodeWithApp!!.id)
                    mConnectionStatus = WearConnectionStatus.CONNECTED
                } catch (e: ApiException) {
                    if (e.statusCode == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED) {
                        mConnectionStatus = WearConnectionStatus.DISCONNECTED
                    } else {
                        Log.e(TAG, "Error", e)
                    }
                }
            }
        }
    }

    private suspend fun checkIfPhoneHasApp(): Node? {
        var node: Node? = null

        try {
            val capabilityInfo = Wearable.getCapabilityClient(context)
                .getCapability(
                    WearableHelper.CAPABILITY_PHONE_APP,
                    CapabilityClient.FILTER_ALL
                )
                .await()
            node = pickBestNodeId(capabilityInfo.nodes)
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
        }

        return node
    }

    suspend fun connect(): Boolean {
        if (mPhoneNodeWithApp == null)
            mPhoneNodeWithApp = checkIfPhoneHasApp()

        return mPhoneNodeWithApp != null
    }

    suspend fun getRemoteTimerStatus(): RemoteTimerTileState =
        suspendCancellableCoroutine { continuation ->
            val listener = MessageClient.OnMessageReceivedListener { event ->
                if (event.path == SleepTimerHelper.SleepTimerStartPath || event.path == SleepTimerHelper.SleepTimerStatusPath) {
                    onMessageReceived(event)
                    if (continuation.isActive) {
                        continuation.resume(
                            RemoteTimerTileState(
                                isLocalTimer = false,
                                connectionStatus = mConnectionStatus,
                                timerModel = timerModel
                            )
                        )
                        return@OnMessageReceivedListener
                    }
                }

                if (continuation.isActive) {
                    continuation.resume(
                        RemoteTimerTileState(
                            isLocalTimer = false,
                            connectionStatus = mConnectionStatus,
                            timerModel = timerModel
                        )
                    )
                }
            }

            continuation.invokeOnCancellation {
                Wearable.getMessageClient(context)
                    .removeListener(listener)
            }

            scope.launch {
                Wearable.getMessageClient(context)
                    .addListener(listener)
                    .await()

                if (connect()) {
                    sendMessage(mPhoneNodeWithApp!!.id, SleepTimerHelper.SleepTimerStatusPath, null)
                }
            }
        }

    suspend fun requestTimerStart(timerLengthInMins: Int): Boolean {
        return if (connect()) {
            runCatching {
                sendMessage(
                    mPhoneNodeWithApp!!.id, SleepTimerHelper.SleepTimerStartPath,
                    timerLengthInMins.intToBytes()
                )
                true
            }.getOrDefault(false)
        } else {
            false
        }
    }

    /*
     * There should only ever be one phone in a node set (much less w/ the correct capability), so
     * I am just grabbing the first one (which should be the only one).
    */
    private fun pickBestNodeId(nodes: Collection<Node>): Node? {
        var bestNode: Node? = null

        // Find a nearby node/phone or pick one arbitrarily. Realistically, there is only one phone.
        for (node in nodes) {
            if (node.isNearby) {
                return node
            }
            bestNode = node
        }
        return bestNode
    }

    suspend fun getConnectedNodes(): List<Node> {
        try {
            return Wearable.getNodeClient(context)
                .connectedNodes
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
        }

        return emptyList()
    }

    private suspend fun sendMessage(nodeID: String, path: String, data: ByteArray?) {
        try {
            Wearable.getMessageClient(context)
                .sendMessage(nodeID, path, data)
                .await()
        } catch (e: Exception) {
            if (e is ApiException || e.cause is ApiException) {
                val apiException = e.cause as? ApiException ?: e as? ApiException
                if (apiException?.statusCode == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED) {
                    // no-op
                }
            }

            Log.e(TAG, "Error", e)
        }
    }

    @Throws(ApiException::class)
    suspend fun sendPing(nodeID: String) {
        try {
            Wearable.getMessageClient(context)
                .sendMessage(nodeID, WearableHelper.PingPath, null).await()
        } catch (e: Exception) {
            if (e is ApiException || e.cause is ApiException) {
                val apiException = e.cause as? ApiException ?: e as ApiException
                throw apiException
            }
            Log.e(TAG, "Error", e)
        }
    }
}