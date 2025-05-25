package com.thewizrd.simplesleeptimer.viewmodels

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.phone.interactions.PhoneTypeHelper
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityClient.OnCapabilityChangedListener
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient.OnMessageReceivedListener
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableStatusCodes
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplesleeptimer.helpers.showConfirmationOverlay
import com.thewizrd.simplesleeptimer.utils.ErrorMessage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

abstract class WearableListenerViewModel(private val app: Application) : AndroidViewModel(app),
    OnMessageReceivedListener, OnCapabilityChangedListener {
    protected val appContext: Context
        get() = app.applicationContext

    @SuppressLint("StaticFieldLeak")
    protected var activityContext: Activity? = null

    @Volatile
    protected var mPhoneNodeWithApp: Node? = null
    private var mConnectionStatus = WearConnectionStatus.CONNECTING

    protected val remoteActivityHelper: RemoteActivityHelper = RemoteActivityHelper(appContext)

    protected val _eventsFlow = MutableSharedFlow<WearableEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val eventFlow: SharedFlow<WearableEvent> = _eventsFlow

    protected val _channelEventsFlow = MutableSharedFlow<WearableEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val channelEventsFlow: SharedFlow<WearableEvent> = _channelEventsFlow

    protected val _errorMessagesFlow = MutableSharedFlow<ErrorMessage>(replay = 0)
    val errorMessagesFlow: SharedFlow<ErrorMessage> = _errorMessagesFlow

    init {
        Wearable.getCapabilityClient(appContext)
            .addListener(this, WearableHelper.CAPABILITY_PHONE_APP)
        Wearable.getMessageClient(appContext).addListener(this)
    }

    fun initActivityContext(activity: Activity) {
        activityContext = activity
    }

    override fun onCleared() {
        super.onCleared()
        Wearable.getCapabilityClient(appContext)
            .removeListener(this, WearableHelper.CAPABILITY_PHONE_APP)
        Wearable.getMessageClient(appContext).removeListener(this)
        activityContext = null
    }

    fun openAppOnPhone(activity: Activity, showAnimation: Boolean = true) {
        viewModelScope.launch {
            connect()

            if (mPhoneNodeWithApp == null) {
                _errorMessagesFlow.tryEmit(ErrorMessage.String("Device is not connected or app is not installed on device..."))

                when (PhoneTypeHelper.getPhoneDeviceType(appContext)) {
                    PhoneTypeHelper.DEVICE_TYPE_ANDROID -> {
                        openPlayStore(activity, showAnimation)
                    }

                    PhoneTypeHelper.DEVICE_TYPE_IOS -> {
                        _errorMessagesFlow.tryEmit(ErrorMessage.String("Connected device is not supported"))
                    }

                    else -> {
                        _errorMessagesFlow.tryEmit(ErrorMessage.String("Connected device is not supported"))
                    }
                }
            } else {
                // Send message to device to start activity
                val result = sendMessage(
                    mPhoneNodeWithApp!!.id,
                    WearableHelper.StartActivityPath,
                    ByteArray(0)
                )

                if (showAnimation) {
                    activity.showConfirmationOverlay(result != -1)
                }

                _eventsFlow.tryEmit(WearableEvent(ACTION_OPENONPHONE, Bundle().apply {
                    putBoolean(EXTRA_SUCCESS, result != -1)
                    putBoolean(EXTRA_SHOWANIMATION, showAnimation)
                }))
            }
        }
    }

    suspend fun openPlayStore(activity: Activity, showAnimation: Boolean = true) {
        // Open store on remote device
        val intentAndroid = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(WearableHelper.getPlayStoreURI())

        runCatching {
            remoteActivityHelper.startRemoteActivity(intentAndroid)
                .await()

            if (showAnimation) {
                activity.showConfirmationOverlay(true)
            }
        }.onFailure {
            if (it !is CancellationException && showAnimation) {
                activity.showConfirmationOverlay(false)
            }
        }
    }

    suspend fun startRemoteActivity(intent: Intent): Boolean {
        return runCatching {
            remoteActivityHelper.startRemoteActivity(intent).await()
            true
        }.onFailure {
            Logger.error(TAG, it, "Error starting remote activity")
        }.getOrDefault(false)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {}

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        viewModelScope.launch {
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
                            Logger.error(TAG, e, "Error")
                        }
                    }
                }
            }

            _eventsFlow.tryEmit(WearableEvent(ACTION_UPDATECONNECTIONSTATUS, Bundle().apply {
                putInt(EXTRA_CONNECTIONSTATUS, mConnectionStatus.value)
            }))
        }
    }

    protected suspend fun updateConnectionStatus() {
        checkConnectionStatus()

        _eventsFlow.tryEmit(WearableEvent(ACTION_UPDATECONNECTIONSTATUS, Bundle().apply {
            putInt(EXTRA_CONNECTIONSTATUS, mConnectionStatus.value)
        }))
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
                        Logger.error(TAG, e, "Error")
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
            val capabilityInfo = Wearable.getCapabilityClient(appContext)
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
            return Wearable.getNodeClient(appContext)
                .connectedNodes
                .await()
        } catch (e: Exception) {
            Logger.error(TAG, e, "Error")
        }

        return emptyList()
    }

    protected suspend fun sendMessage(nodeID: String, path: String, data: ByteArray?): Int? {
        try {
            return Wearable.getMessageClient(appContext)
                .sendMessage(nodeID, path, data).await()
        } catch (e: Exception) {
            if (e is ApiException || e.cause is ApiException) {
                val apiException = e.cause as? ApiException ?: e as? ApiException
                if (apiException?.statusCode == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED) {
                    mConnectionStatus = WearConnectionStatus.DISCONNECTED

                    _eventsFlow.tryEmit(
                        WearableEvent(
                            ACTION_UPDATECONNECTIONSTATUS,
                            Bundle().apply {
                                putInt(EXTRA_CONNECTIONSTATUS, mConnectionStatus.value)
                            })
                    )
                }
            }

            Logger.error(TAG, e, "Error")
        }

        return -1
    }

    @Throws(ApiException::class)
    protected suspend fun sendPing(nodeID: String) {
        try {
            Wearable.getMessageClient(appContext)
                .sendMessage(nodeID, WearableHelper.PingPath, null).await()
        } catch (e: Exception) {
            if (e is ApiException || e.cause is ApiException) {
                val apiException = e.cause as? ApiException ?: e as ApiException
                throw apiException
            }
            Logger.error(TAG, e, "Error")
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
    protected fun setConnectionStatus(status: WearConnectionStatus) {
        mConnectionStatus = status

        _eventsFlow.tryEmit(WearableEvent(ACTION_UPDATECONNECTIONSTATUS, Bundle().apply {
            putInt(EXTRA_CONNECTIONSTATUS, mConnectionStatus.value)
        }))
    }

    companion object {
        private const val TAG = "WearableListenerViewModel"

        // Actions
        const val ACTION_OPENONPHONE = "SimpleWear.Droid.Wear.action.OPEN_APP_ON_PHONE"
        const val ACTION_SHOWSTORELISTING = "SimpleWear.Droid.Wear.action.SHOW_STORE_LISTING"
        const val ACTION_UPDATECONNECTIONSTATUS =
            "SimpleWear.Droid.Wear.action.UPDATE_CONNECTION_STATUS"
        const val ACTION_SHOWCONFIRMATION = "SimpleWear.Droid.Wear.action.SHOW_CONFIRMATION"

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
         * Extra contains Action data (serialized class in JSON) to be passed to BroadcastReceiver or Activity
         *
         * @see WearableEvent
         */
        const val EXTRA_EVENTDATA = "SimpleWear.Droid.Wear.extra.EVENT_DATA"

        /**
         * Extra contains Status data (serialized class in JSON) for complex Status types
         *
         * @see BatteryStatus
         */
        const val EXTRA_STATUS = "SimpleWear.Droid.Wear.extra.STATUS"

        /**
         * Extra contains connection status for WearOS device and connected phone
         *
         * @see WearConnectionStatus
         */
        const val EXTRA_CONNECTIONSTATUS = "SimpleWear.Droid.Wear.extra.CONNECTION_STATUS"
    }
}