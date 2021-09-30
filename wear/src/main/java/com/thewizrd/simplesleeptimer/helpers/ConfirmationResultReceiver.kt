package com.thewizrd.simplesleeptimer.helpers

import android.app.Activity
import androidx.wear.widget.ConfirmationOverlay

fun Activity.showConfirmationOverlay(success: Boolean) {
    val overlay = ConfirmationOverlay()
    if (!success) {
        overlay.setType(ConfirmationOverlay.FAILURE_ANIMATION)
    } else {
        overlay.setType(ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION)
    }
    overlay.showOn(this)
}