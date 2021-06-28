package com.thewizrd.shared_resources.adapters

import androidx.core.util.ObjectsCompat
import androidx.recyclerview.widget.DiffUtil
import com.thewizrd.shared_resources.viewmodels.MusicPlayerViewModel

class MusicPlayerItemDiffer : DiffUtil.ItemCallback<MusicPlayerViewModel>() {
    override fun areItemsTheSame(
        oldItem: MusicPlayerViewModel,
        newItem: MusicPlayerViewModel
    ): Boolean {
        return ObjectsCompat.equals(oldItem.packageName, newItem.packageName) &&
                ObjectsCompat.equals(oldItem.activityName, newItem.activityName)
    }

    override fun areContentsTheSame(
        oldItem: MusicPlayerViewModel,
        newItem: MusicPlayerViewModel
    ): Boolean {
        return ObjectsCompat.equals(oldItem, newItem)
    }
}