package com.thewizrd.simplesleeptimer.adapters

import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thewizrd.shared_resources.adapters.MusicPlayerItemDiffer
import com.thewizrd.shared_resources.helpers.RecyclerOnClickListenerInterface
import com.thewizrd.shared_resources.viewmodels.MusicPlayerViewModel
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.controls.WearChipButton
import com.thewizrd.simplesleeptimer.preferences.Settings

open class PlayerListAdapter :
    ListAdapter<MusicPlayerViewModel, PlayerListAdapter.ViewHolder>(MusicPlayerItemDiffer()) {
    protected var mCheckedPosition = RecyclerView.NO_POSITION
    private var onClickListener: RecyclerOnClickListenerInterface? = null

    class Payload {
        companion object {
            const val RADIOBUTTON_UPDATE: Int = 0
        }
    }

    fun setOnClickListener(listener: RecyclerOnClickListenerInterface?) {
        onClickListener = listener
    }

    inner class ViewHolder(val item: WearChipButton) : RecyclerView.ViewHolder(item)

    protected open fun onMusicPlayerSelected(key: String?) {
        Settings.setMusicPlayer(key)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = WearChipButton(
            parent.context,
            defStyleAttr = 0,
            defStyleRes = R.style.Widget_Wear_WearChipButton_Surface_Checkable
        ).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            updateControlType(WearChipButton.CONTROLTYPE_RADIO)
        }

        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val viewModel = getItem(position)
        if (viewModel.bitmapIcon != null) {
            holder.item.setIconDrawable(viewModel.bitmapIcon?.toDrawable(holder.itemView.context.resources))
        } else {
            holder.item.setIconResource(R.drawable.ic_play_circle_filled)
        }
        holder.item.setPrimaryText(viewModel.appLabel)
        holder.item.setOnClickListener {
            val oldPosition = mCheckedPosition
            if (mCheckedPosition != position) {
                mCheckedPosition = position
                notifyItemChanged(oldPosition, Payload.RADIOBUTTON_UPDATE)
                notifyItemChanged(mCheckedPosition, Payload.RADIOBUTTON_UPDATE)
            } else {
                // uncheck
                mCheckedPosition = RecyclerView.NO_POSITION
                notifyItemChanged(oldPosition, Payload.RADIOBUTTON_UPDATE)
            }

            onMusicPlayerSelected(selectedItem?.key)
            onClickListener?.onClick(holder.itemView, position)
        }
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        val radioBtnUpdateOnly = if (payloads.isNotEmpty()) {
            payloads[0] == Payload.RADIOBUTTON_UPDATE
        } else {
            false
        }

        if (!radioBtnUpdateOnly) {
            super.onBindViewHolder(holder, position, payloads)
        }

        if (mCheckedPosition == RecyclerView.NO_POSITION) {
            holder.item.isChecked = false
        } else {
            holder.item.isChecked = mCheckedPosition == position
        }
    }

    open fun updateItems(dataset: List<MusicPlayerViewModel>) {
        val currentPref = Settings.getMusicPlayer()
        val item = dataset.find { item -> item.key != null && item.key == currentPref }
        mCheckedPosition = if (item != null) {
            dataset.indexOf(item)
        } else {
            RecyclerView.NO_POSITION
        }

        submitList(dataset)
    }

    val selectedItem: MusicPlayerViewModel?
        get() {
            if (mCheckedPosition != RecyclerView.NO_POSITION) {
                return getItem(mCheckedPosition)
            }

            return null
        }
}