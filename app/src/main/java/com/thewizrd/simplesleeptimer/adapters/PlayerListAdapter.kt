package com.thewizrd.simplesleeptimer.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thewizrd.shared_resources.adapters.MusicPlayerItemDiffer
import com.thewizrd.shared_resources.helpers.RecyclerOnClickListenerInterface
import com.thewizrd.shared_resources.viewmodels.MusicPlayerViewModel
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.databinding.MusicplayerItemBinding
import com.thewizrd.simplesleeptimer.preferences.Settings

class PlayerListAdapter : ListAdapter<MusicPlayerViewModel, PlayerListAdapter.ViewHolder>(
    MusicPlayerItemDiffer()
) {
    private var mCheckedPosition: Int = RecyclerView.NO_POSITION
    private var onClickListener: RecyclerOnClickListenerInterface? = null

    class Payload {
        companion object {
            const val RADIOBUTTON_UPDATE: Int = 0
        }
    }

    fun setOnClickListener(listener: RecyclerOnClickListenerInterface?) {
        onClickListener = listener
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private var binding = MusicplayerItemBinding.bind(itemView)

        fun bindModel(viewModel: MusicPlayerViewModel) {
            if (viewModel.bitmapIcon == null) {
                binding.playerIcon.setImageResource(R.drawable.ic_play_circle_filled)
                ImageViewCompat.setImageTintList(
                    binding.playerIcon,
                    ColorStateList.valueOf(
                        ContextCompat.getColor(
                            itemView.context,
                            R.color.colorOnBackground
                        )
                    )
                )
            } else {
                binding.playerIcon.setImageBitmap(viewModel.bitmapIcon)
            }
            binding.playerName.text = viewModel.appLabel
        }

        fun updateRadioButton() {
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

                Settings.setMusicPlayer(getSelectedItem()?.key)
                onClickListener?.onClick(itemView, adapterPosition)
            }
            itemView.setOnClickListener(clickListener)
            binding.radioButton.setOnClickListener(clickListener)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.musicplayer_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindModel(getItem(position))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        val radioBtnUpdateOnly = if (payloads.isNotEmpty()) {
            payloads[0] == Payload.RADIOBUTTON_UPDATE
        } else {
            false
        }

        if (!radioBtnUpdateOnly) {
            super.onBindViewHolder(holder, position, payloads)
        }

        holder.updateRadioButton()
    }

    fun updateItems(dataset: List<MusicPlayerViewModel>) {
        val currentPref = Settings.getMusicPlayer()
        val item = dataset.find { item -> item.key != null && item.key == currentPref }
        mCheckedPosition = if (item != null) {
            dataset.indexOf(item)
        } else {
            RecyclerView.NO_POSITION
        }

        submitList(dataset)
    }

    fun getSelectedItem(): MusicPlayerViewModel? {
        if (mCheckedPosition != RecyclerView.NO_POSITION) {
            return getItem(mCheckedPosition)
        }

        return null
    }
}