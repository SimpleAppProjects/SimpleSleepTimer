package com.thewizrd.simplesleeptimer.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import com.thewizrd.simplesleeptimer.App

object Settings {
    private val playerPreferences: SharedPreferences =
        App.instance.appContext.getSharedPreferences("players", Context.MODE_PRIVATE)

    private const val KEY_MUSICPLAYER: String = "key_musicplayer"
    private const val KEY_BRIDGETIMER: String = "key_bridgetimer"
    private const val KEY_LASTTIME_SET: String = "key_lasttime_set"

    fun getMusicPlayer(): String? {
        return playerPreferences.getString(KEY_MUSICPLAYER, null)
    }

    fun setMusicPlayer(player: String?) {
        if (player != null) {
            playerPreferences.edit().putString(KEY_MUSICPLAYER, player).apply()
        } else {
            playerPreferences.edit().remove(KEY_MUSICPLAYER).apply()
        }
    }

    fun isBridgeTimerEnabled(): Boolean {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
        return preferences.getBoolean(KEY_BRIDGETIMER, false)
    }

    fun setBridgeTimerEnabled(value: Boolean) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
        preferences.edit {
            putBoolean(KEY_BRIDGETIMER, value)
        }
    }

    fun getLastTimeSet(): Int {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
        return preferences.getInt(KEY_LASTTIME_SET, TimerModel.DEFAULT_TIME_MIN)
    }

    fun setLastTimeSet(timeInMins: Int) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(App.instance.appContext)
        preferences.edit().putInt(KEY_LASTTIME_SET, timeInMins).apply()
    }
}