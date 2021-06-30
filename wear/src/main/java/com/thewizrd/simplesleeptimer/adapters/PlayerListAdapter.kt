package com.thewizrd.simplesleeptimer.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thewizrd.shared_resources.adapters.MusicPlayerItemDiffer
import com.thewizrd.shared_resources.helpers.RecyclerOnClickListenerInterface
import com.thewizrd.shared_resources.viewmodels.MusicPlayerViewModel
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.databinding.ItemHeaderBinding
import com.thewizrd.simplesleeptimer.databinding.MusicplayerItemBinding
import com.thewizrd.simplesleeptimer.preferences.Settings

open class PlayerListAdapter :
    ListAdapter<MusicPlayerViewModel, RecyclerView.ViewHolder>(MusicPlayerItemDiffer()) {
    companion object {
        private const val HEADER_TYPE = 0
        private const val ITEM_TYPE = 1
    }

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

    inner class HeaderViewHolder(binding: ItemHeaderBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class ViewHolder(private val binding: MusicplayerItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindModel(viewModel: MusicPlayerViewModel) {
            if (viewModel.bitmapIcon == null) {
                binding.appIcon.setImageResource(R.drawable.ic_play_circle_filled)
            } else {
                binding.appIcon.setImageBitmap(viewModel.bitmapIcon)
            }
            binding.appName.text = viewModel.appLabel
        }

        fun updateRadioButtom() {
            if (mCheckedPosition == RecyclerView.NO_POSITION) {
                binding.radioButton.isChecked = false
            } else {
                binding.radioButton.isChecked = mCheckedPosition == adapterPosition
            }

            val clickListener = View.OnClickListener {
                val oldPosition = mCheckedPosition
                if (mCheckedPosition != adapterPosition) {
                    mCheckedPosition = adapterPosition
                    notifyItemChanged(oldPosition, Payload.RADIOBUTTON_UPDATE)
                    notifyItemChanged(mCheckedPosition, Payload.RADIOBUTTON_UPDATE)
                } else {
                    // uncheck
                    mCheckedPosition = RecyclerView.NO_POSITION
                    notifyItemChanged(oldPosition, Payload.RADIOBUTTON_UPDATE)
                }

                onMusicPlayerSelected(selectedItem?.key)
                onClickListener?.onClick(itemView, adapterPosition)
            }
            itemView.setOnClickListener(clickListener)
            binding.radioButton.setOnClickListener(clickListener)
        }
    }

    protected open fun onMusicPlayerSelected(key: String?) {
        Settings.setMusicPlayer(key)
    }

    override fun getItemCount(): Int {
        return super.getItemCount() + 1
    }

    override fun getItem(position: Int): MusicPlayerViewModel {
        return super.getItem(position - 1)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) {
            HEADER_TYPE
        } else {
            ITEM_TYPE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == HEADER_TYPE) {
            HeaderViewHolder(
                ItemHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        } else {
            ViewHolder(
                MusicplayerItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolder) {
            holder.bindModel(getItem(position))
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        if (holder is ViewHolder) {
            val radioBtnUpdateOnly = if (payloads.isNotEmpty()) {
                payloads[0] == Payload.RADIOBUTTON_UPDATE
            } else {
                false
            }

            if (!radioBtnUpdateOnly) {
                super.onBindViewHolder(holder, position, payloads)
            }

            holder.updateRadioButtom()
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