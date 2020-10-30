package com.thewizrd.simplesleeptimer

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate

class App : Application(), ActivityLifecycleCallbacks {
    private lateinit var context: Context
    private lateinit var applicationState: AppState
    private var mActivitiesStarted: Int = 0

    companion object {
        private lateinit var instance: App
        fun getInstance(): App {
            return instance
        }
    }

    fun getAppContext(): Context {
        return context
    }

    fun getAppState(): AppState {
        return applicationState
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        instance = this

        registerActivityLifecycleCallbacks(this)
        applicationState = AppState.CLOSED
        mActivitiesStarted = 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        else
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
    }

    override fun onTerminate() {
        super.onTerminate()
        unregisterActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.localClassName.contains("LaunchActivity") ||
            activity.localClassName.contains("MainActivity")
        ) {
            applicationState = AppState.FOREGROUND
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (mActivitiesStarted == 0)
            applicationState = AppState.FOREGROUND

        mActivitiesStarted++
    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
        mActivitiesStarted--

        if (mActivitiesStarted == 0)
            applicationState = AppState.BACKGROUND
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity.localClassName.contains("MainActivity")) {
            applicationState = AppState.CLOSED
        }
    }
}