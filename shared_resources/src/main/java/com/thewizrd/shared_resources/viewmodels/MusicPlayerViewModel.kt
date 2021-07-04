package com.thewizrd.shared_resources.viewmodels

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import java.util.*

class MusicPlayerViewModel : ViewModel() {
    var bitmapIcon: Bitmap? = null
    var appLabel: String? = null
    var packageName: String? = null
    var activityName: String? = null
    val key: String?
        get() {
            if (packageName != null && activityName != null) {
                return String.format(Locale.ROOT, "%s/%s", packageName, activityName)
            }
            return null
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MusicPlayerViewModel

        if (appLabel != other.appLabel) return false
        if (packageName != other.packageName) return false
        if (activityName != other.activityName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = appLabel?.hashCode() ?: 0
        result = 31 * result + (packageName?.hashCode() ?: 0)
        result = 31 * result + (activityName?.hashCode() ?: 0)
        return result
    }
}