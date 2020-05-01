package com.thewizrd.simplesleeptimer

import android.view.View
import com.google.android.material.bottomsheet.BottomSheetBehavior

interface BottomSheetCallbackInterface {
    fun onBottomSheetBehaviorInitialized(bottomSheet: View)

    /**
     * Called when the bottom sheet changes its state.
     *
     * @param bottomSheet The bottom sheet view.
     * @param newState The new state. This will be one of {@link #STATE_DRAGGING}, {@link
     *     #STATE_SETTLING}, {@link #STATE_EXPANDED}, {@link #STATE_COLLAPSED}, {@link
     *     #STATE_HIDDEN}, or {@link #STATE_HALF_EXPANDED}.
     */
    fun onStateChanged(bottomSheet: View, @BottomSheetBehavior.State newState: Int)

    /**
     * Called when the bottom sheet is being dragged.
     *
     * @param bottomSheet The bottom sheet view.
     * @param slideOffset The new offset of this bottom sheet within [-1,1] range. Offset increases
     *     as this bottom sheet is moving upward. From 0 to 1 the sheet is between collapsed and
     *     expanded states and from -1 to 0 it is between hidden and collapsed states.
     */
    fun onSlide(bottomSheet: View, slideOffset: Float)
}