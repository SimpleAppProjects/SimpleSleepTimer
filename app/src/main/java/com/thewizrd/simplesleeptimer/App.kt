package com.thewizrd.simplesleeptimer

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.jakewharton.threetenabp.AndroidThreeTen

class App : Application() {
    private lateinit var context: Context

    companion object {
        private lateinit var instance: App
        fun getInstance(): App {
            return instance
        }
    }

    fun getAppContext(): Context {
        return context
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        instance = this

        AndroidThreeTen.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        else
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
    }

    override fun onTerminate() {
        super.onTerminate()
    }
}