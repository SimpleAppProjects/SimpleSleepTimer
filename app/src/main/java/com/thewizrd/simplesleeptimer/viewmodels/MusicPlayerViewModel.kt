package com.thewizrd.simplesleeptimer.viewmodels

import android.graphics.Bitmap
import java.util.*

class MusicPlayerViewModel {
    var mBitmapIcon: Bitmap? = null
    var mAppLabel: String? = null
    var mPackageName: String? = null
    var mActivityName: String? = null

    public fun getKey(): String? {
        if (mPackageName != null && mActivityName != null) {
            return String.format(Locale.ROOT, "%s/%s", mPackageName, mActivityName)
        }

        return null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MusicPlayerViewModel

        if (mBitmapIcon != other.mBitmapIcon) return false
        if (mAppLabel != other.mAppLabel) return false
        if (mPackageName != other.mPackageName) return false
        if (mActivityName != other.mActivityName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = mBitmapIcon?.hashCode() ?: 0
        result = 31 * result + (mAppLabel?.hashCode() ?: 0)
        result = 31 * result + (mPackageName?.hashCode() ?: 0)
        result = 31 * result + (mActivityName?.hashCode() ?: 0)
        return result
    }
}