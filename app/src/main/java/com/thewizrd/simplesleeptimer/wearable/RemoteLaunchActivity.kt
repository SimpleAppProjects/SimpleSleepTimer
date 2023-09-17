package com.thewizrd.simplesleeptimer.wearable

import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.ComponentActivity
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.helpers.WearableHelper.toLaunchIntent

@SuppressLint("CustomSplashScreen")
class RemoteLaunchActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()

        intent?.data?.let { uri ->
            if (WearableHelper.isRemoteLaunchUri(uri)) {
                runCatching {
                    this.startActivity(uri.toLaunchIntent())
                }.onFailure { e ->
                    Log.e(this::class.java.simpleName, "Unable to launch intent remotely - $uri", e)
                }
            }
        }

        finishAffinity()
    }
}