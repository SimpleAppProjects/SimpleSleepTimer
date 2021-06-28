package com.thewizrd.simplesleeptimer.preferences

import android.content.Context
import android.content.SharedPreferences
import com.thewizrd.simplesleeptimer.App

class Settings {
    companion object {
        private val preferences: SharedPreferences =
            App.instance.appContext.getSharedPreferences("players", Context.MODE_PRIVATE)

        private const val KEY_MUSICPLAYER: String = "key_musicplayer"

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
    }
}