package com.thewizrd.simplesleeptimer.wearable

import android.companion.CompanionDeviceManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.service.media.MediaBrowserService
import android.util.TypedValue
import androidx.annotation.RestrictTo
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityClient.OnCapabilityChangedListener
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.thewizrd.shared_resources.data.AppItemData
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.media.MusicPlayersData
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.shared_resources.utils.ImageUtils
import com.thewizrd.shared_resources.utils.ImageUtils.toByteArray
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.booleanToBytes
import com.thewizrd.shared_resources.utils.stringToBytes
import com.thewizrd.simplesleeptimer.preferences.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Collections

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class WearableManager(private val mContext: Context) : OnCapabilityChangedListener {
    companion object {
        private const val TAG = "WearableManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var mCapabilityClient: CapabilityClient
    private var mWearNodesWithApp: Collection<Node>? = null

    init {
        init()
    }

    private fun init() {
        mCapabilityClient = Wearable.getCapabilityClient(mContext)
        mCapabilityClient.addListener(this, WearableHelper.CAPABILITY_WEAR_APP)
        scope.launch {
            mWearNodesWithApp = findWearDevicesWithApp()
        }
    }

    fun unregister() {
        scope.cancel()
        mCapabilityClient.removeListener(this)
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        mWearNodesWithApp = capabilityInfo.nodes
    }

    private suspend fun findWearDevicesWithApp(): Collection<Node>? {
        var capabilityInfo: CapabilityInfo? = null
        try {
            capabilityInfo = mCapabilityClient.getCapability(
                WearableHelper.CAPABILITY_WEAR_APP,
                CapabilityClient.FILTER_ALL
            )
                .await()
        } catch (e: Exception) {
            Logger.error(TAG, e, "Error")
        }
        return capabilityInfo?.nodes
    }

    suspend fun startMusicPlayer(nodeID: String?, pkgName: String, activityName: String?) {
        if (!pkgName.isNullOrBlank() && !activityName.isNullOrBlank()) {
            val appIntent = Intent().apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_APP_MUSIC)
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK).component =
                    ComponentName(pkgName, activityName)
            }

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                mContext.startActivity(appIntent)
            } else {
                // Android Q+ Devices
                // Android Q puts a limitation on starting activities from the background
                // We are allowed to bypass this if we have a device registered as companion,
                // which will be our WearOS device; Check if device is associated before we start
                // OR if SYSTEM_ALERT_WINDOW is granted
                val deviceManager =
                    mContext.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
                val associated_devices = deviceManager.associations
                if (associated_devices.isNotEmpty() || android.provider.Settings.canDrawOverlays(
                        mContext
                    )
                ) {
                    mContext.startActivity(appIntent)
                } else {
                    // No devices associated; send message to user
                    sendMessage(nodeID, WearableHelper.OpenMusicPlayerPath, false.booleanToBytes())
                }
            }
        }
    }

    suspend fun sendSupportedMusicPlayers(nodeID: String) {
        val appInfos = mutableListOf<ApplicationInfo>()

        /* Media Button Receivers */
        mContext.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_MEDIA_BUTTON), PackageManager.GET_RESOLVED_FILTER
        ).mapTo(appInfos) { it.activityInfo.applicationInfo }

        /* MediaBrowser services */
        mContext.packageManager.queryIntentServices(
            Intent(MediaBrowserService.SERVICE_INTERFACE),
            PackageManager.GET_RESOLVED_FILTER
        ).mapTo(appInfos) { it.serviceInfo.applicationInfo }

        // Sort result
        Collections.sort(
            appInfos,
            ApplicationInfo.DisplayNameComparator(mContext.packageManager)
        )

        val supportedPlayers = ArrayList<String>(appInfos.size)
        val musicPlayers = mutableSetOf<AppItemData>()

        suspend fun addPlayerInfo(appInfo: ApplicationInfo) {
            val launchIntent =
                mContext.packageManager.getLaunchIntentForPackage(appInfo.packageName)
            if (launchIntent != null) {
                val activityInfo = mContext.packageManager.resolveActivity(
                    launchIntent,
                    PackageManager.MATCH_DEFAULT_ONLY
                )
                    ?: return
                val activityCmpName =
                    ComponentName(appInfo.packageName, activityInfo.activityInfo.name)
                val key =
                    String.format("%s/%s", appInfo.packageName, activityInfo.activityInfo.name)
                if (!supportedPlayers.contains(key)) {
                    val label = mContext.packageManager.getApplicationLabel(appInfo).toString()
                    var iconBmp: Bitmap? = null
                    try {
                        val iconDrwble = mContext.packageManager.getActivityIcon(activityCmpName)
                        val size = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            36f,
                            mContext.resources.displayMetrics
                        ).toInt()
                        iconBmp = ImageUtils.bitmapFromDrawable(iconDrwble, size, size)
                    } catch (ignored: PackageManager.NameNotFoundException) {
                    }

                    musicPlayers.add(
                        AppItemData(
                            label = label,
                            packageName = appInfo.packageName,
                            activityName = activityInfo.activityInfo.name,
                            iconBitmap = iconBmp?.toByteArray()
                        )
                    )
                    supportedPlayers.add(key)
                }
            }
        }

        for (info in appInfos) {
            addPlayerInfo(info)
        }

        val playersData = MusicPlayersData(musicPlayers = musicPlayers)

        try {
            val channelClient = Wearable.getChannelClient(mContext)

            withContext(Dispatchers.IO) {
                val channel =
                    channelClient.openChannel(nodeID, WearableHelper.MusicPlayersPath).await()
                val outputStream = channelClient.getOutputStream(channel).await()
                outputStream.bufferedWriter().use { writer ->
                    writer.write(
                        "data: ${
                            JSONParser.serializer(
                                playersData,
                                MusicPlayersData::class.java
                            )
                        }"
                    )
                    writer.newLine()
                    writer.flush()
                }
                channelClient.close(channel)
            }
        } catch (e: Exception) {
            Logger.error(TAG, e, "Error")
        }
    }

    fun sendSleepTimerStart(model: TimerModel) {
        scope.launch {
            sendMessage(
                null, SleepTimerHelper.SleepTimerStartPath,
                JSONParser.serializer(model, TimerModel::class.java).stringToBytes()
            )
            sendSleepTimerStatusBridge(model)
        }
    }

    fun sendSleepTimerUpdate(model: TimerModel) {
        scope.launch {
            sendSleepTimerUpdate(null, model)
            sendSleepTimerStatusBridge(model)
        }
    }

    suspend fun sendSleepTimerStatusBridge(model: TimerModel) {
        if (Settings.isBridgeTimerEnabled()) {
            sendMessage(
                null,
                SleepTimerHelper.SleepTimerBridgePath,
                JSONParser.serializer(model, TimerModel::class.java)?.stringToBytes()
            )
        }
    }

    suspend fun removeSleepTimerStatusBridge() {
        sendMessage(
            null,
            SleepTimerHelper.SleepTimerBridgePath,
            null
        )
    }

    fun sendSleepCancelled() {
        scope.launch {
            sendMessage(null, SleepTimerHelper.SleepTimerStopPath, null)
            removeSleepTimerStatusBridge()
        }
    }

    suspend fun sendSleepTimerUpdate(nodeID: String?, model: TimerModel) {
        sendMessage(
            nodeID, SleepTimerHelper.SleepTimerStatusPath,
            JSONParser.serializer(model, TimerModel::class.java).stringToBytes()
        )
    }

    suspend fun sendSelectedAudioPlayer(nodeID: String?) {
        sendMessage(
            nodeID, SleepTimerHelper.SleepTimerAudioPlayerPath,
            Settings.getMusicPlayer()?.stringToBytes()
        )
    }

    suspend fun sendMessage(nodeID: String?, path: String, data: ByteArray?) {
        if (nodeID == null) {
            if (mWearNodesWithApp == null) {
                // Create requests if nodes exist with app support
                mWearNodesWithApp = findWearDevicesWithApp()
                if (mWearNodesWithApp == null || mWearNodesWithApp!!.isEmpty()) return
            }
        }
        if (nodeID != null) {
            try {
                Wearable.getMessageClient(mContext)
                    .sendMessage(nodeID, path, data)
                    .await()
            } catch (e: Exception) {
                Logger.error(TAG, e, "Error")
            }
        } else {
            for (node in mWearNodesWithApp!!) {
                try {
                    Wearable.getMessageClient(mContext)
                        .sendMessage(node.id, path, data)
                        .await()
                } catch (e: Exception) {
                    Logger.error(TAG, e, "Error")
                }
            }
        }
    }
}