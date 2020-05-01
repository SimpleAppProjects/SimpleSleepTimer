package com.thewizrd.simplesleeptimer.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.util.ObjectsCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.databinding.MusicplayerItemBinding
import com.thewizrd.simplesleeptimer.helpers.RecyclerOnClickListenerInterface
import com.thewizrd.simplesleeptimer.preferences.Settings
import com.thewizrd.simplesleeptimer.viewmodels.MusicPlayerViewModel

class PlayerListAdapter : RecyclerView.Adapter<PlayerListAdapter.ViewHolder>() {
    private var mDiffer: AsyncListDiffer<MusicPlayerViewModel>
    private var mCheckedPosition: Int = RecyclerView.NO_POSITION
    private var onClickListener: RecyclerOnClickListenerInterface? = null

    init {
        mDiffer = AsyncListDiffer(this, diffCallback)
    }

    class Payload {
        companion object {
            const val RADIOBUTTON_UPDATE: Int = 0
        }
    }

    fun setOnClickListener(listener: RecyclerOnClickListenerInterface?) {
        onClickListener = listener
    }

    private object diffCallback : DiffUtil.ItemCallback<MusicPlayerViewModel>() {
        override fun areItemsTheSame(
            oldItem: MusicPlayerViewModel,
            newItem: MusicPlayerViewModel
        ): Boolean {
            return ObjectsCompat.equals(oldItem.mPackageName, newItem.mPackageName) &&
                    ObjectsCompat.equals(oldItem.mActivityName, newItem.mActivityName)
        }

        override fun areContentsTheSame(
            oldItem: MusicPlayerViewModel,
            newItem: MusicPlayerViewModel
        ): Boolean {
            return ObjectsCompat.equals(oldItem, newItem)
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private var binding: MusicplayerItemBinding

        init {
            binding = MusicplayerItemBinding.bind(itemView)
        }

        fun bindModel(viewModel: MusicPlayerViewModel) {
            if (viewModel.mBitmapIcon == null) {
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
                binding.playerIcon.setImageBitmap(viewModel.mBitmapIcon)
            }
            binding.playerName.text = viewModel.mAppLabel
        }

        fun updateRadioButton() {
            if (mCheckedPosition == RecyclerView.NO_POSITION) {
                binding.radioButton.isChecked = false
            } else {
                if (mCheckedPosition == adapterPosition) {
                    binding.radioButton.isChecked = true
                } else {
                    binding.radioButton.isChecked = false
                }
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

                Settings.setMusicPlayer(getSelectedItem()?.getKey())
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

    override fun getItemCount(): Int {
        return mDiffer.currentList.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindModel(mDiffer.currentList[position])
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        val radioBtnUpdateOnly: Boolean
        if (payloads.isNotEmpty()) {
            radioBtnUpdateOnly = payloads[0] == Payload.RADIOBUTTON_UPDATE
        } else {
            radioBtnUpdateOnly = false
        }

        if (!radioBtnUpdateOnly) {
            super.onBindViewHolder(holder, position, payloads)
        }

        holder.updateRadioButton()
    }

    fun updateItems(dataset: List<MusicPlayerViewModel>) {
        val currentPref = Settings.getMusicPlayer()
        val item = dataset.find { item -> item.getKey() != null && item.getKey() == currentPref }
        if (item != null) {
            mCheckedPosition = dataset.indexOf(item)
        } else {
            mCheckedPosition = RecyclerView.NO_POSITION
        }

        mDiffer.submitList(dataset)
    }

    fun getSelectedItem(): MusicPlayerViewModel? {
        if (mCheckedPosition != RecyclerView.NO_POSITION) {
            return mDiffer.currentList[mCheckedPosition]
        }

        return null
    }
}