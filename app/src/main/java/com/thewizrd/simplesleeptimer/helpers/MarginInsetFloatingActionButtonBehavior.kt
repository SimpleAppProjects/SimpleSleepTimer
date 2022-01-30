package com.thewizrd.simplesleeptimer.helpers

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.marginTop
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MarginInsetFloatingActionButtonBehavior : FloatingActionButton.Behavior {
    constructor() : super()
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun getInsetDodgeRect(
        parent: CoordinatorLayout,
        child: FloatingActionButton,
        rect: Rect
    ): Boolean {
        super.getInsetDodgeRect(parent, child, rect)

        rect.set(
            rect.left + child.marginLeft,
            rect.top + child.marginTop,
            rect.right - child.marginRight,
            rect.bottom - child.marginBottom
        )

        return true
    }
}