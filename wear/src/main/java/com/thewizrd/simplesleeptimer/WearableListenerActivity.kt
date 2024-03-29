package com.thewizrd.simplesleeptimer

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.wear.phone.interactions.PhoneTypeHelper
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.*
import com.google.android.gms.wearable.CapabilityClient.OnCapabilityChangedListener
import com.google.android.gms.wearable.MessageClient.OnMessageReceivedListener
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.simplesleeptimer.activities.AppCompatLiteActivity
import com.thewizrd.simplesleeptimer.helpers.showConfirmationOverlay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

abstract class WearableListenerActivity : AppCompatLiteActivity(), OnMessageReceivedListener,
    OnCapabilityChangedListener {
    companion object {
        private const val TAG = "WearListenerActivity"

        // Actions
        const val ACTION_OPENONPHONE = "SimpleWear.Droid.Wear.action.OPEN_APP_ON_PHONE"
        const val ACTION_SHOWSTORELISTING = "SimpleWear.Droid.Wear.action.SHOW_STORE_LISTING"
        const val ACTION_UPDATECONNECTIONSTATUS =
            "SimpleWear.Droid.Wear.action.UPDATE_CONNECTION_STATUS"

        // Extras
        /**
         * Extra contains success flag for open on phone action.
         *
         * @see ACTION_OPENONPHONE
         */
        const val EXTRA_SUCCESS = "SimpleWear.Droid.Wear.extra.SUCCESS"

        /**
         * Extra contains flag for whether or not to show the animation for the open on phone action.
         *
         * @see ACTION_OPENONPHONE
         */
        const val EXTRA_SHOWANIMATION = "SimpleWear.Droid.Wear.extra.SHOW_ANIMATION"

        /**
         * Extra contains connection status for WearOS device and connected phone
         *
         * @see WearConnectionStatus
         *
         * @see WearableListenerActivity
         */
        const val EXTRA_CONNECTIONSTATUS = "SimpleWear.Droid.Wear.extra.CONNECTION_STATUS"
    }

    @Volatile
    protected var mPhoneNodeWithApp: Node? = null
    protected var mConnectionStatus = WearConnectionStatus.DISCONNECTED

    protected abstract val broadcastReceiver: BroadcastReceiver
    protected abstract val intentFilter: IntentFilter

    protected lateinit var remoteActivityHelper: RemoteActivityHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        remoteActivityHelper = RemoteActivityHelper(this)
    }

    override fun onResume() {
        super.onResume()

        Wearable.getCapabilityClient(this).addListener(this, WearableHelper.CAPABILITY_PHONE_APP)
        Wearable.getMessageClient(this).addListener(this)

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, intentFilter)
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(broadcastReceiver)
        Wearable.getCapabilityClient(this).removeListener(this, WearableHelper.CAPABILITY_PHONE_APP)
        Wearable.getMessageClient(this).removeListener(this)
        super.onPause()
    }

    protected fun openAppOnPhone(showAnimation: Boolean = true) {
        lifecycleScope.launch {
            connect()

            if (mPhoneNodeWithApp == null) {
                Toast.makeText(
                    this@WearableListenerActivity,
                    "Device is not connected or app is not installed on device...",
                    Toast.LENGTH_SHORT
                ).show()

                when (PhoneTypeHelper.getPhoneDeviceType(this@WearableListenerActivity)) {
                    PhoneTypeHelper.DEVICE_TYPE_ANDROID -> {
                        // Open store on remote device
                        val intentAndroid = Intent(Intent.ACTION_VIEW)
                            .addCategory(Intent.CATEGORY_BROWSABLE)
                            .setData(WearableHelper.getPlayStoreURI())

                        runCatching {
                            remoteActivityHelper.startRemoteActivity(intentAndroid)
                                .await()

                            showConfirmationOverlay(true)
                        }.onFailure {
                            if (it !is CancellationException) {
                                showConfirmationOverlay(false)
                            }
                        }
                    }
                    PhoneTypeHelper.DEVICE_TYPE_IOS -> {
                        Toast.makeText(
                            this@WearableListenerActivity,
                            "Connected device is not supported",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    else -> {
                        Toast.makeText(
                            this@WearableListenerActivity,
                            "Connected device is not supported",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                // Send message to device to start activity
                val result = sendMessage(
                    mPhoneNodeWithApp!!.id,
                    WearableHelper.StartActivityPath,
                    ByteArray(0)
                )

                LocalBroadcastManager.getInstance(this@WearableListenerActivity)
                    .sendBroadcast(
                        Intent(ACTION_OPENONPHONE)
                            .putExtra(EXTRA_SUCCESS, result != -1)
                            .putExtra(EXTRA_SHOWANIMATION, showAnimation)
                    )
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {}

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        lifecycleScope.launch {
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
                mConnectionStatus = if (connectedNodes.isNullOrEmpty()) {
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

            LocalBroadcastManager.getInstance(this@WearableListenerActivity)
                .sendBroadcast(
                    Intent(ACTION_UPDATECONNECTIONSTATUS)
                        .putExtra(EXTRA_CONNECTIONSTATUS, mConnectionStatus.value)
                )
        }
    }

    protected suspend fun updateConnectionStatus() {
        checkConnectionStatus()

        LocalBroadcastManager.getInstance(this@WearableListenerActivity)
            .sendBroadcast(
                Intent(ACTION_UPDATECONNECTIONSTATUS)
                    .putExtra(EXTRA_CONNECTIONSTATUS, mConnectionStatus.value)
            )
    }

    protected suspend fun checkConnectionStatus() {
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
            mConnectionStatus = if (connectedNodes.isNullOrEmpty()) {
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

    suspend fun getConnectionStatus(): WearConnectionStatus {
        checkConnectionStatus()
        return mConnectionStatus
    }

    protected suspend fun checkIfPhoneHasApp(): Node? {
        var node: Node? = null

        try {
            val capabilityInfo = Wearable.getCapabilityClient(this@WearableListenerActivity)
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

    protected suspend fun connect(): Boolean {
        if (mPhoneNodeWithApp == null)
            mPhoneNodeWithApp = checkIfPhoneHasApp()

        return mPhoneNodeWithApp != null
    }

    /*
     * There should only ever be one phone in a node set (much less w/ the correct capability), so
     * I am just grabbing the first one (which should be the only one).
     */
    protected fun pickBestNodeId(nodes: Collection<Node>): Node? {
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

    private suspend fun getConnectedNodes(): List<Node> {
        try {
            return Wearable.getNodeClient(this)
                .connectedNodes
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
        }

        return emptyList()
    }

    protected suspend fun sendMessage(nodeID: String, path: String, data: ByteArray?): Int? {
        try {
            return Wearable.getMessageClient(this@WearableListenerActivity)
                .sendMessage(nodeID, path, data).await()
        } catch (e: Exception) {
            if (e is ApiException || e.cause is ApiException) {
                val apiException = e.cause as? ApiException ?: e as? ApiException
                if (apiException?.statusCode == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED) {
                    mConnectionStatus = WearConnectionStatus.DISCONNECTED

                    LocalBroadcastManager.getInstance(this@WearableListenerActivity)
                        .sendBroadcast(
                            Intent(ACTION_UPDATECONNECTIONSTATUS)
                                .putExtra(EXTRA_CONNECTIONSTATUS, mConnectionStatus.value)
                        )
                }
            }

            Log.e(TAG, "Error", e)
        }

        return -1
    }

    @Throws(ApiException::class)
    protected suspend fun sendPing(nodeID: String) {
        try {
            Wearable.getMessageClient(this@WearableListenerActivity)
                .sendMessage(nodeID, WearableHelper.PingPath, null).await()
        } catch (e: Exception) {
            if (e is ApiException || e.cause is ApiException) {
                val apiException = e.cause as? ApiException ?: e as ApiException
                throw apiException
            }
            Log.e(TAG, "Error", e)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
    protected fun setConnectionStatus(status: WearConnectionStatus) {
        mConnectionStatus = status

        LocalBroadcastManager.getInstance(this@WearableListenerActivity)
            .sendBroadcast(
                Intent(ACTION_UPDATECONNECTIONSTATUS)
                    .putExtra(EXTRA_CONNECTIONSTATUS, mConnectionStatus.value)
            )
    }
}