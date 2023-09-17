package com.thewizrd.simplesleeptimer.utils

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

suspend inline fun <reified S : Service, B : IBinder> Context.connectService(
    crossinline onDisconnect: () -> Unit = {}
): Pair<B, ServiceConnection> = suspendCoroutine {
    val connection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            onDisconnect()
        }

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            it.resume(binder as B to this)
        }
    }

    applicationContext.bindService(
        Intent(this, S::class.java),
        connection,
        Context.BIND_AUTO_CREATE
    )
}