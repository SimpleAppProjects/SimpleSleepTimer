package com.thewizrd.simplesleeptimer.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.thewizrd.shared_resources.appLib
import com.thewizrd.shared_resources.sleeptimer.TimerModel

object Settings {
    private val preferences: SharedPreferences =
        appLib.context.getSharedPreferences("players", Context.MODE_PRIVATE)

    private const val KEY_MUSICPLAYER: String = "key_musicplayer"
    private const val KEY_LASTTIME_SET: String = "key_lasttime_set"

    fun getMusicPlayer(): String? {
        return preferences.getString(KEY_MUSICPLAYER, null)
    }

    fun setMusicPlayer(player: String?) {
        if (player != null) {
            preferences.edit { putString(KEY_MUSICPLAYER, player) }
        } else {
            preferences.edit { remove(KEY_MUSICPLAYER) }
        }
    }

    fun getLastTimeSet(): Int {
        return preferences.getInt(KEY_LASTTIME_SET, TimerModel.DEFAULT_TIME_MIN)
    }

    fun setLastTimeSet(timeInMins: Int) {
        preferences.edit { putInt(KEY_LASTTIME_SET, timeInMins) }
    }
}