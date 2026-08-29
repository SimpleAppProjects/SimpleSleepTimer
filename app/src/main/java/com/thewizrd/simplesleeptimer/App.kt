package com.thewizrd.simplesleeptimer

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.work.Configuration
import com.google.android.material.color.DynamicColors
import com.thewizrd.shared_resources.ApplicationLib
import com.thewizrd.shared_resources.SharedModule
import com.thewizrd.shared_resources.appLib
import com.thewizrd.shared_resources.helpers.AppState
import com.thewizrd.shared_resources.sharedDeps
import com.thewizrd.shared_resources.utils.Logger
import kotlinx.coroutines.cancel

class App : Application(), ActivityLifecycleCallbacks, Configuration.Provider {
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
            override val isPhone = true
        }

        sharedDeps = object : SharedModule() {
            override val context = appLib.context // keep same context as applib
        }

        FirebaseConfigurator.initialize(applicationContext)

        // Debugging
        if (BuildConfig.DEBUG) {
            val threadPolicy = StrictMode.ThreadPolicy.Builder()
                .detectCustomSlowCalls()
                .penaltyLog()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                threadPolicy.detectResourceMismatches()
            }

            StrictMode.setThreadPolicy(threadPolicy.build())

            val vmPolicy = VmPolicy.Builder()
                .detectActivityLeaks()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                vmPolicy.detectCleartextNetwork()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                vmPolicy.detectNonSdkApiUsage()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                vmPolicy.detectIncorrectContextUse()
                    .detectUnsafeIntentLaunch()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                vmPolicy.detectBlockedBackgroundActivityLaunch()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
                vmPolicy.detectImplicitUriPermissionGrant()
            }

            StrictMode.setVmPolicy(vmPolicy.build())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        else
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)

        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    override fun onTerminate() {
        // Shutdown logger
        Logger.shutdown()
        appLib.appScope.cancel()
        super.onTerminate()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.localClassName.contains(SleepTimerActivity::class.java.simpleName)) {
            applicationState = AppState.FOREGROUND
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (mActivitiesStarted == 0) applicationState = AppState.FOREGROUND
        mActivitiesStarted++
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity.localClassName.contains(SleepTimerActivity::class.java.simpleName)) {
            applicationState = AppState.FOREGROUND
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (activity.localClassName.contains(SleepTimerActivity::class.java.simpleName)) {
            applicationState = AppState.BACKGROUND
        }
    }

    override fun onActivityStopped(activity: Activity) {
        mActivitiesStarted--
        if (mActivitiesStarted == 0) applicationState = AppState.BACKGROUND
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (activity.localClassName.contains(SleepTimerActivity::class.java.simpleName)) {
            applicationState = AppState.CLOSED
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
            .build()
}