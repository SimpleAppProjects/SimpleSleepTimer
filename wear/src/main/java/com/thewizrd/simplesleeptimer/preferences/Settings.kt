package com.thewizrd.simplesleeptimer.preferences

import android.content.Context
import android.content.SharedPreferences
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.simplesleeptimer.App

object Settings {
    private val preferences: SharedPreferences =
        App.instance.appContext.getSharedPreferences("players", Context.MODE_PRIVATE)

    private const val KEY_MUSICPLAYER: String = "key_musicplayer"
    private const val KEY_LASTTIME_SET: String = "key_lasttime_set"

    fun getMusicPlayer(): String? {
        return preferences.getString(KEY_MUSICPLAYER, null)
    }

    fun setMusicPlayer(player: String?) {
        if (player != null) {
            preferences.edit().putString(KEY_MUSICPLAYER, player).apply()
        } else {
            preferences.edit().remove(KEY_MUSICPLAYER).apply()
        }
    }

    fun getLastTimeSet(): Int {
        return preferences.getInt(KEY_LASTTIME_SET, TimerModel.DEFAULT_TIME_MIN)
    }

    fun setLastTimeSet(timeInMins: Int) {
        preferences.edit().putInt(KEY_LASTTIME_SET, timeInMins).apply()
    }
}