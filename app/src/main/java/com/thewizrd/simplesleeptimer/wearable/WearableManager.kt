package com.thewizrd.simplesleeptimer.wearable

import android.companion.CompanionDeviceManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.util.TypedValue
import androidx.annotation.RestrictTo
import com.google.android.gms.wearable.*
import com.google.android.gms.wearable.CapabilityClient.OnCapabilityChangedListener
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.shared_resources.utils.ImageUtils
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.booleanToBytes
import com.thewizrd.shared_resources.utils.stringToBytes
import com.thewizrd.simplesleeptimer.preferences.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.*

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
            Log.e(TAG, "Error", e)
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
                val deviceManager =
                    mContext.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
                val associated_devices = deviceManager.associations
                if (associated_devices.isEmpty()) {
                    // No devices associated; send message to user
                    sendMessage(nodeID, WearableHelper.OpenMusicPlayerPath, false.booleanToBytes())
                } else {
                    mContext.startActivity(appIntent)
                }
            }
        }
    }

    suspend fun sendSupportedMusicPlayers() {
        val infos = mContext.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_MEDIA_BUTTON), PackageManager.GET_RESOLVED_FILTER
        )
        val mapRequest = PutDataMapRequest.create(WearableHelper.MusicPlayersPath)

        // Sort result
        Collections.sort(infos, ResolveInfo.DisplayNameComparator(mContext.packageManager))

        val supportedPlayers = ArrayList<String>(infos.size)

        for (info in infos) {
            val appInfo = info.activityInfo.applicationInfo
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
                    val map = DataMap()
                    map.putString(WearableHelper.KEY_LABEL, label)
                    map.putString(WearableHelper.KEY_PKGNAME, appInfo.packageName)
                    map.putString(WearableHelper.KEY_ACTIVITYNAME, activityInfo.activityInfo.name)
                    map.putAsset(
                        WearableHelper.KEY_ICON,
                        iconBmp?.let { ImageUtils.createAssetFromBitmap(iconBmp) })
                    mapRequest.dataMap.putDataMap(key, map)
                    supportedPlayers.add(key)
                }
            }
        }
        mapRequest.dataMap.putStringArrayList(WearableHelper.KEY_SUPPORTEDPLAYERS, supportedPlayers)
        mapRequest.setUrgent()
        try {
            Wearable.getDataClient(mContext)
                .putDataItem(mapRequest.asPutDataRequest())
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
        }
    }

    fun sendSleepTimerStart(model: TimerModel) {
        scope.launch {
            sendMessage(
                null, SleepTimerHelper.SleepTimerStartPath,
                JSONParser.serializer(model, TimerModel::class.java).stringToBytes()
            )
        }
    }

    fun sendSleepTimerUpdate(model: TimerModel) {
        scope.launch {
            sendSleepTimerUpdate(null, model)
        }
    }

    fun sendSleepCancelled() {
        scope.launch {
            sendMessage(null, SleepTimerHelper.SleepTimerStopPath, null)
        }
    }

    suspend fun sendSleepTimerUpdate(nodeID: String?, model: TimerModel) {
        sendMessage(
            nodeID, SleepTimerHelper.SleepTimerStatusPath,
            JSONParser.serializer(model, TimerModel::class.java).stringToBytes()
        )
    }

    suspend fun sendSelectedAudioPlayer() {
        val mapRequest = PutDataMapRequest.create(SleepTimerHelper.SleepTimerAudioPlayerPath)
        mapRequest.dataMap.putString(SleepTimerHelper.KEY_SELECTEDPLAYER, Settings.getMusicPlayer())
        //mapRequest.setUrgent()
        try {
            Wearable.getDataClient(mContext)
                .putDataItem(mapRequest.asPutDataRequest())
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
        }
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
                Log.e(TAG, "Error", e)
            }
        } else {
            for (node in mWearNodesWithApp!!) {
                try {
                    Wearable.getMessageClient(mContext)
                        .sendMessage(node.id, path, data)
                        .await()
                } catch (e: Exception) {
                    Log.e(TAG, "Error", e)
                }
            }
        }
    }
}