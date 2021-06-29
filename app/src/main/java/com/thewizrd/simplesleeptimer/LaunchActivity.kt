package com.thewizrd.simplesleeptimer

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class LaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)

        startActivity(Intent(this, SleepTimerActivity::class.java))
        finishAffinity()
    }
}