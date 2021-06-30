package com.thewizrd.simplesleeptimer

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class LaunchLocalActivity : Activity() {
    companion object {
        private const val TAG = "LaunchLocalActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(this, SleepTimerLocalActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        // Navigate
        startActivity(intent)
        finishAffinity()
    }
}