package com.thewizrd.simplesleeptimer.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.thewizrd.shared_resources.appLib
import com.thewizrd.shared_resources.sleeptimer.TimerModel
import java.time.Instant

object Settings {
    private val preferences: SharedPreferences =
        appLib.context.getSharedPreferences("players", Context.MODE_PRIVATE)

    private const val KEY_MUSICPLAYER: String = "key_musicplayer"
    private const val KEY_LASTTIME_SET: String = "key_lasttime_set"
    private const val KEY_LASTUPDATECHECK = "key_lastupdatecheck"

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

    fun getLastUpdateCheckTime(): Instant {
        val epochSeconds =
            appLib.preferences.getLong(KEY_LASTUPDATECHECK, Instant.EPOCH.epochSecond)
        return Instant.ofEpochSecond(epochSeconds)
    }

    fun setLastUpdateCheckTime(value: Instant) {
        appLib.preferences.edit {
            putLong(KEY_LASTUPDATECHECK, value.epochSecond)
        }
    }
}