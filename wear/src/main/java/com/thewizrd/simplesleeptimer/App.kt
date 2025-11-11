package com.thewizrd.simplesleeptimer

import android.app.Activity
import android.app.Application
import android.content.SharedPreferences
import android.os.Bundle
import android.os.StrictMode
import android.preference.PreferenceManager
import com.thewizrd.shared_resources.ApplicationLib
import com.thewizrd.shared_resources.SharedModule
import com.thewizrd.shared_resources.appLib
import com.thewizrd.shared_resources.helpers.AppState
import com.thewizrd.shared_resources.sharedDeps
import com.thewizrd.shared_resources.utils.Logger
import kotlinx.coroutines.cancel

class App : Application(), Application.ActivityLifecycleCallbacks {
    private lateinit var applicationState: AppState
    private var mActivitiesStarted = 0

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(this)
        applicationState = AppState.CLOSED
        mActivitiesStarted = 0

        // Initialize app dependencies (library module chain)
        // 1. ApplicationLib + SharedModule, 2. Firebase
        appLib = object : ApplicationLib() {
            override val context = applicationContext
            override val preferences: SharedPreferences
                get() = PreferenceManager.getDefaultSharedPreferences(context)
            override val appState: AppState
                get() = applicationState
            override val isPhone = false
        }

        sharedDeps = object : SharedModule() {
            override val context = appLib.context // keep same context as applib
        }

        FirebaseConfigurator.initialize(applicationContext)

        // Debugging
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectActivityLeaks()
                    .detectLeakedRegistrationObjects()
                    .penaltyLog()
                    .build()
            )
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        // Shutdown logger
        Logger.shutdown()
        appLib.appScope.cancel()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {
        mActivitiesStarted++
    }

    override fun onActivityResumed(activity: Activity) {
        if ((activity is PhoneSyncActivity || activity is SleepTimerActivity || activity is SleepTimerLocalActivity) && applicationState != AppState.FOREGROUND) {
            applicationState = AppState.FOREGROUND
        }
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {
        mActivitiesStarted--
        if (mActivitiesStarted == 0) applicationState = AppState.BACKGROUND
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}