package com.thewizrd.simplesleeptimer.wearable

import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.helpers.WearableHelper.toLaunchIntent
import com.thewizrd.shared_resources.utils.Logger

@SuppressLint("CustomSplashScreen")
class RemoteLaunchActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()

        intent?.data?.let { uri ->
            if (WearableHelper.isRemoteLaunchUri(uri)) {
                runCatching {
                    this.startActivity(uri.toLaunchIntent())
                }.onFailure { e ->
                    Logger.error(
                        this::class.java.simpleName,
                        e,
                        "Unable to launch intent remotely - $uri"
                    )
                }
            }
        }

        finishAffinity()
    }
}