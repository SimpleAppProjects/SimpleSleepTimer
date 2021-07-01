package com.thewizrd.shared_resources.sleeptimer

import android.net.Uri
import com.thewizrd.shared_resources.BuildConfig

object SleepTimerHelper {
    // Link to Play Store listing
    private const val PACKAGE_NAME = "com.thewizrd.simplesleeptimer"
    private const val PLAY_STORE_APP_URI = "market://details?id=$PACKAGE_NAME"

    fun getPlayStoreURI(): Uri = Uri.parse(PLAY_STORE_APP_URI)

    const val SleepTimerStartPath = "/status/sleeptimer/start"
    const val SleepTimerStopPath = "/status/sleeptimer/stop"
    const val SleepTimerStatusPath = "/status/sleeptimer/status"
    const val SleepTimerAudioPlayerPath = "/sleeptimer/audioplayer"
    const val SleepTimerUpdateStatePath = "/status/sleeptimer/update"

    // For Music Player DataMap
    const val KEY_SELECTEDPLAYER = "key_selected_player"
    const val ACTION_START_TIMER = "SimpleSleepTimer.action.START_TIMER"
    const val ACTION_CANCEL_TIMER = "SimpleSleepTimer.action.CANCEL_TIMER"
    const val EXTRA_TIME_IN_MINS = "SimpleSleepTimer.extra.TIME_IN_MINUTES"

    fun getPackageName(): String {
        var packageName = PACKAGE_NAME
        if (BuildConfig.DEBUG) packageName += ".debug"
        return packageName
    }
}