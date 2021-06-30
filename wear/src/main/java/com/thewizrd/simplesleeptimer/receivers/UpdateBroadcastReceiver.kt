package com.thewizrd.simplesleeptimer.receivers

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.thewizrd.simplesleeptimer.LaunchLocalActivity

class UpdateBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val pm = context.packageManager

                if (pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)) {
                    pm.setComponentEnabledSetting(
                        ComponentName(context.applicationContext, LaunchLocalActivity::class.java),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                } else {
                    pm.setComponentEnabledSetting(
                        ComponentName(context.applicationContext, LaunchLocalActivity::class.java),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        }
    }
}