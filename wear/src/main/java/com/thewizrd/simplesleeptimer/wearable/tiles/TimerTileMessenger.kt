package com.thewizrd.simplesleeptimer.wearable.tiles

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableStatusCodes
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.intToBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TimerTileMessenger(private val context: Context) :
    CapabilityClient.OnCapabilityChangedListener {
    companion object {
        private const val TAG = "TimerTileMessenger"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var mPhoneNodeWithApp: Node? = null

    private val _connectionState = MutableStateFlow(WearConnectionStatus.DISCONNECTED)
    val connectionState = _connectionState.stateIn(
        scope,
        SharingStarted.Eagerly,
        _connectionState.value
    )

    fun register() {
        Wearable.getCapabilityClient(context)
            .addListener(this, WearableHelper.CAPABILITY_PHONE_APP)
    }

    fun unregister() {
        Wearable.getCapabilityClient(context)
            .removeListener(this, WearableHelper.CAPABILITY_PHONE_APP)

        scope.cancel()
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        scope.launch {
            val connectedNodes = getConnectedNodes()
            mPhoneNodeWithApp = WearableHelper.pickBestNodeId(capabilityInfo.nodes)
            mPhoneNodeWithApp?.let { node ->
                if (node.isNearby && connectedNodes.any { it.id == node.id }) {
                    _connectionState.update { WearConnectionStatus.CONNECTED }
                } else {
                    try {
                        sendPing(node.id)
                        _connectionState.update { WearConnectionStatus.CONNECTED }
                    } catch (e: ApiException) {
                        if (e.statusCode == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED) {
                            _connectionState.update { WearConnectionStatus.DISCONNECTED }
                        } else {
                            Logger.writeLine(Log.ERROR, e)
                        }
                    }
                }
            } ?: run {
                /*
                 * If a device is disconnected from the wear network, capable nodes are empty
                 *
                 * No capable nodes can mean the app is not installed on the remote device or the
                 * device is disconnected.
                 *
                 * Verify if we're connected to any nodes; if not, we're truly disconnected
                 */
                _connectionState.update {
                    if (connectedNodes.isEmpty()) {
                        WearConnectionStatus.DISCONNECTED
                    } else {
                        WearConnectionStatus.APPNOTINSTALLED
                    }
                }
            }
        }
    }

    suspend fun checkConnectionStatus() {
        val connectedNodes = getConnectedNodes()
        mPhoneNodeWithApp = checkIfPhoneHasApp()

        mPhoneNodeWithApp?.let { node ->
            if (node.isNearby && connectedNodes.any { it.id == node.id }) {
                _connectionState.update { WearConnectionStatus.CONNECTED }
            } else {
                try {
                    sendPing(node.id)
                    _connectionState.update { WearConnectionStatus.CONNECTED }
                } catch (e: ApiException) {
                    if (e.statusCode == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED) {
                        _connectionState.update { WearConnectionStatus.DISCONNECTED }
                    } else {
                        Logger.error(TAG, e)
                    }
                }
            }
        } ?: run {
            /*
             * If a device is disconnected from the wear network, capable nodes are empty
             *
             * No capable nodes can mean the app is not installed on the remote device or the
             * device is disconnected.
             *
             * Verify if we're connected to any nodes; if not, we're truly disconnected
             */
            _connectionState.update {
                if (connectedNodes.isEmpty()) {
                    WearConnectionStatus.DISCONNECTED
                } else {
                    WearConnectionStatus.APPNOTINSTALLED
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
            Logger.error(TAG, e, "Error")
        }

        return node
    }

    suspend fun connect(): Boolean {
        if (mPhoneNodeWithApp == null)
            mPhoneNodeWithApp = checkIfPhoneHasApp()

        return mPhoneNodeWithApp != null
    }

    suspend fun requestUpdate() {
        if (connect()) {
            sendMessage(mPhoneNodeWithApp!!.id, SleepTimerHelper.SleepTimerStatusPath, null)
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

    suspend fun requestTimerStop() {
        if (connect()) {
            sendMessage(mPhoneNodeWithApp!!.id, SleepTimerHelper.SleepTimerStopPath, null)
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
            Logger.error(TAG, e, "Error")
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

            Logger.error(TAG, e, "Error")
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
            Logger.error(TAG, e, "Error")
        }
    }
}