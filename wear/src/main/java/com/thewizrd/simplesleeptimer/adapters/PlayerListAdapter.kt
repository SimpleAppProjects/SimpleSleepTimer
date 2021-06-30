package com.thewizrd.simplesleeptimer.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thewizrd.shared_resources.adapters.MusicPlayerItemDiffer
import com.thewizrd.shared_resources.helpers.RecyclerOnClickListenerInterface
import com.thewizrd.shared_resources.viewmodels.MusicPlayerViewModel
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.SelectedPlayerViewModel
import com.thewizrd.simplesleeptimer.databinding.ItemHeaderBinding
import com.thewizrd.simplesleeptimer.databinding.MusicplayerItemSleeptimerBinding

class PlayerListAdapter(owner: ViewModelStoreOwner) :
    ListAdapter<MusicPlayerViewModel, RecyclerView.ViewHolder>(MusicPlayerItemDiffer()) {
    companion object {
        private const val HEADER_TYPE = 0
        private const val ITEM_TYPE = 1
    }

    private var mCheckedPosition = RecyclerView.NO_POSITION
    private var onClickListener: RecyclerOnClickListenerInterface? = null

    private val selectedPlayer =
        ViewModelProvider(owner, ViewModelProvider.NewInstanceFactory())
            .get(SelectedPlayerViewModel::class.java)

    private object Payload {
        var RADIOBUTTON_UPDATE = 0
    }

    fun setOnClickListener(listener: RecyclerOnClickListenerInterface?) {
        onClickListener = listener
    }

    inner class HeaderViewHolder(binding: ItemHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {}

    inner class ViewHolder(private val binding: MusicplayerItemSleeptimerBinding) :
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

                selectedPlayer.setKey(selectedItem?.key)

                onClickListener?.onClick(itemView, adapterPosition)
            }
            itemView.setOnClickListener(clickListener)
            binding.radioButton.setOnClickListener(clickListener)
        }
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
                MusicplayerItemSleeptimerBinding.inflate(
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

    fun updateItems(dataset: List<MusicPlayerViewModel>) {
        val currentPref = selectedPlayer.key.value
        var item: MusicPlayerViewModel? = null

        for (it in dataset) {
            if (it.key != null && it.key == currentPref) {
                item = it
                break
            }
        }

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