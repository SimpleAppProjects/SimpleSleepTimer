package com.thewizrd.shared_resources.helpers

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.wearable.PutDataRequest
import com.thewizrd.shared_resources.SimpleLibrary
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper

object WearableHelper {
    // Name of capability listed in Phone app's wear.xml
    const val CAPABILITY_PHONE_APP = "com.thewizrd.simplesleeptimer_phone_app"

    // Name of capability listed in Wear app's wear.xml
    const val CAPABILITY_WEAR_APP = "com.thewizrd.simplesleeptimer_wear_app"

    fun getPlayStoreURI(): Uri = SleepTimerHelper.getPlayStoreURI()

    // For WearableListenerService
    const val StartActivityPath = "/start-activity"
    const val StartPermissionsActivityPath = "/start-activity/permissions"
    const val MusicPlayersPath = "/music-players"
    const val OpenMusicPlayerPath = "/music/start-activity"
    const val BtDiscoverPath = "/bluetooth/discoverable"
    const val PingPath = "/ping"

    // For Music Player DataMap
    const val KEY_SUPPORTEDPLAYERS = "key_supported_players"
    const val KEY_APPS = "key_apps"
    const val KEY_LABEL = "key_label"
    const val KEY_ICON = "key_icon"
    const val KEY_PKGNAME = "key_package_name"
    const val KEY_ACTIVITYNAME = "key_activity_name"

    // For Activity Launcher
    private const val SCHEME_APP = "simplesleeptimer"
    private const val PATH_REMOTE_LAUNCH = "launch-activity"
    const val URI_PARAM_PKGNAME = "package"
    const val URI_PARAM_ACTIVITYNAME = "activity"

    fun isGooglePlayServicesInstalled(): Boolean {
        val queryResult = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(SimpleLibrary.instance.app.appContext)
        if (queryResult == ConnectionResult.SUCCESS) {
            Log.println(Log.INFO, "App", "Google Play Services is installed on this device.")
            return true
        }
        if (GoogleApiAvailability.getInstance().isUserResolvableError(queryResult)) {
            val errorString = GoogleApiAvailability.getInstance().getErrorString(queryResult)
            Log.println(
                Log.INFO,
                "App",
                "There is a problem with Google Play Services on this device: $queryResult - $errorString"
            )
        }
        return false
    }

    fun getWearDataUri(NodeId: String?, Path: String?): Uri {
        return Uri.Builder()
            .scheme(PutDataRequest.WEAR_URI_SCHEME)
            .authority(NodeId)
            .path(Path)
            .build()
    }

    fun getWearDataUri(Path: String?): Uri {
        return Uri.Builder()
            .scheme(PutDataRequest.WEAR_URI_SCHEME)
            .path(Path)
            .build()
    }

    fun getLaunchActivityUri(packageName: String, activityName: String): Uri {
        return Uri.Builder()
            .scheme(SCHEME_APP)
            .authority(PATH_REMOTE_LAUNCH)
            .appendQueryParameter(URI_PARAM_PKGNAME, packageName)
            .appendQueryParameter(URI_PARAM_ACTIVITYNAME, activityName)
            .build()
    }

    fun createRemoteActivityIntent(packageName: String, activityName: String): Intent {
        return Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(getLaunchActivityUri(packageName, activityName))
    }

    fun isRemoteLaunchUri(uri: Uri): Boolean {
        return uri.scheme == SCHEME_APP && uri.host == PATH_REMOTE_LAUNCH &&
                !uri.getQueryParameter(URI_PARAM_PKGNAME).isNullOrEmpty() &&
                !uri.getQueryParameter(URI_PARAM_ACTIVITYNAME).isNullOrEmpty()
    }

    fun Uri.toLaunchIntent(): Intent {
        return Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .setComponent(
                ComponentName(
                    this.getQueryParameter(URI_PARAM_PKGNAME)!!,
                    this.getQueryParameter(URI_PARAM_ACTIVITYNAME)!!
                )
            )
    }
}