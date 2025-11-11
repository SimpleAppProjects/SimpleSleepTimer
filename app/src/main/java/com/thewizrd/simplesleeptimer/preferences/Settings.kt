package com.thewizrd.simplesleeptimer.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.thewizrd.shared_resources.appLib
import com.thewizrd.shared_resources.sleeptimer.TimerModel

object Settings {
    private val playerPreferences: SharedPreferences =
        appLib.context.getSharedPreferences("players", Context.MODE_PRIVATE)

    private const val KEY_MUSICPLAYER: String = "key_musicplayer"
    private const val KEY_BRIDGETIMER: String = "key_bridgetimer"
    private const val KEY_LASTTIME_SET: String = "key_lasttime_set"

    fun getMusicPlayer(): String? {
        return playerPreferences.getString(KEY_MUSICPLAYER, null)
    }

    fun setMusicPlayer(player: String?) {
        if (player != null) {
            playerPreferences.edit { putString(KEY_MUSICPLAYER, player) }
        } else {
            playerPreferences.edit { remove(KEY_MUSICPLAYER) }
        }
    }

    fun isBridgeTimerEnabled(): Boolean {
        return appLib.preferences.getBoolean(KEY_BRIDGETIMER, false)
    }

    fun setBridgeTimerEnabled(value: Boolean) {
        appLib.preferences.edit {
            putBoolean(KEY_BRIDGETIMER, value)
        }
    }

    fun getLastTimeSet(): Int {
        return appLib.preferences.getInt(KEY_LASTTIME_SET, TimerModel.DEFAULT_TIME_MIN)
    }

    fun setLastTimeSet(timeInMins: Int) {
        appLib.preferences.edit { putInt(KEY_LASTTIME_SET, timeInMins) }
    }
}