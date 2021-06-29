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
import com.thewizrd.simplesleeptimer.databinding.MusicplayerItemSleeptimerBinding

class PlayerListAdapter(owner: ViewModelStoreOwner) :
    ListAdapter<MusicPlayerViewModel, PlayerListAdapter.ViewHolder>(MusicPlayerItemDiffer()) {
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

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val binding = MusicplayerItemSleeptimerBinding.bind(itemView)

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.musicplayer_item_sleeptimer, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindModel(getItem(position))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
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