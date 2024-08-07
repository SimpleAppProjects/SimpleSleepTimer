package com.thewizrd.simplesleeptimer

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.StrictMode
import com.thewizrd.shared_resources.ApplicationLib
import com.thewizrd.shared_resources.SimpleLibrary
import com.thewizrd.shared_resources.helpers.AppState

class App : Application(), ApplicationLib, Application.ActivityLifecycleCallbacks {
    companion object {
        @JvmStatic
        lateinit var instance: ApplicationLib
            private set
    }

    override lateinit var appContext: Context
        private set
    override lateinit var applicationState: AppState
        private set
    private var mActivitiesStarted = 0
    override val isPhone: Boolean = false

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        instance = this

        registerActivityLifecycleCallbacks(this)
        applicationState = AppState.CLOSED
        mActivitiesStarted = 0

        // Init shared library
        SimpleLibrary.initialize(this)

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
        SimpleLibrary.unregister()
        super.onTerminate()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {
        mActivitiesStarted++
    }

    override fun onActivityResumed(activity: Activity) {
        if ((activity is WearableListenerActivity || activity is SleepTimerLocalActivity) && applicationState != AppState.FOREGROUND) {
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