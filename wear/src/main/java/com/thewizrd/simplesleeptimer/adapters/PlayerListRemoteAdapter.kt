package com.thewizrd.simplesleeptimer.adapters

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.recyclerview.widget.RecyclerView
import com.thewizrd.shared_resources.viewmodels.MusicPlayerViewModel
import com.thewizrd.simplesleeptimer.SelectedPlayerViewModel

class PlayerListRemoteAdapter(owner: ViewModelStoreOwner) : PlayerListAdapter() {
    private val selectedPlayer =
        ViewModelProvider(owner, ViewModelProvider.NewInstanceFactory())
            .get(SelectedPlayerViewModel::class.java)

    override fun onMusicPlayerSelected(key: String?) {
        selectedPlayer.setKey(key)
    }

    override fun updateItems(dataset: List<MusicPlayerViewModel>) {
        val currentPref = selectedPlayer.key.value
        val item = dataset.find { item -> item.key != null && item.key == currentPref }
        mCheckedPosition = if (item != null) {
            dataset.indexOf(item)
        } else {
            RecyclerView.NO_POSITION
        }

        submitList(dataset)
    }
}