package com.thewizrd.simplesleeptimer

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.os.Bundle
import android.view.*
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.InputDeviceCompat
import androidx.core.view.MotionEventCompat
import androidx.core.view.ViewConfigurationCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.wear.widget.WearableLinearLayoutManager
import com.thewizrd.shared_resources.utils.ContextUtils.dpToPx
import com.thewizrd.shared_resources.viewmodels.MusicPlayerViewModel
import com.thewizrd.simplesleeptimer.adapters.ListHeaderAdapter
import com.thewizrd.simplesleeptimer.adapters.PlayerListAdapter
import com.thewizrd.simplesleeptimer.adapters.SpacerAdapter
import com.thewizrd.simplesleeptimer.databinding.FragmentMusicPlayersBinding
import com.thewizrd.simplesleeptimer.helpers.CustomScrollingLayoutCallback
import com.thewizrd.simplesleeptimer.helpers.SpacerItemDecoration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.roundToInt

class MusicPlayersLocalFragment : Fragment() {
    companion object {
        private const val TAG = "MusicPlayersFragment"
    }

    private lateinit var binding: FragmentMusicPlayersBinding
    private lateinit var playerAdapter: PlayerListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMusicPlayersBinding.inflate(inflater, container, false)

        binding.playerList.setHasFixedSize(true)
        //binding.playerList.isEdgeItemsCenteringEnabled = false
        binding.playerList.addItemDecoration(
            SpacerItemDecoration(
                requireContext().dpToPx(16f).toInt(),
                requireContext().dpToPx(4f).toInt()
            )
        )
        binding.playerList.layoutManager =
            WearableLinearLayoutManager(requireActivity(), CustomScrollingLayoutCallback())
        binding.playerList.setOnGenericMotionListener { v, ev ->
            if (ev.action == MotionEvent.ACTION_SCROLL &&
                ev.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)
            ) {
                // Don't forget the negation here
                val delta = -ev.getAxisValue(MotionEventCompat.AXIS_SCROLL) *
                        ViewConfigurationCompat.getScaledVerticalScrollFactor(
                            ViewConfiguration.get(v.context), v.context
                        )
                // Swap these axes to scroll horizontally instead
                v.scrollBy(0, delta.roundToInt())
                true
            } else {
                false
            }
        }
        binding.playerList.requestFocus()

        playerAdapter = PlayerListAdapter()
        binding.playerList.adapter = ConcatAdapter(
            ListHeaderAdapter(getString(R.string.select_player_pause_prompt)),
            playerAdapter,
            SpacerAdapter(requireContext().dpToPx(48f).toInt())
        )
        binding.playerList.visibility = View.GONE

        binding.retryFab.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                updateSupportedMusicPlayers()
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    private fun showProgressBar(show: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (show) {
                binding.progressBar.show()
                binding.noplayersView.visibility = View.GONE
                binding.playerList.visibility = View.GONE
            } else {
                binding.progressBar.hide()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        showProgressBar(true)
        updateSupportedMusicPlayers()
    }

    private fun updateSupportedMusicPlayers() {
        val infos = requireContext().packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_MEDIA_BUTTON), PackageManager.GET_RESOLVED_FILTER
        )

        // Sort result
        Collections.sort(infos, ResolveInfo.DisplayNameComparator(requireContext().packageManager))

        val supportedPlayers = ArrayList<String>(infos.size)
        val playerModels = ArrayList<MusicPlayerViewModel>(infos.size)

        for (info in infos) {
            val appInfo = info.activityInfo.applicationInfo
            val launchIntent =
                requireContext().packageManager.getLaunchIntentForPackage(appInfo.packageName)
            if (launchIntent != null) {
                val activityInfo = requireContext().packageManager.resolveActivity(
                    launchIntent,
                    PackageManager.MATCH_DEFAULT_ONLY
                )
                    ?: continue
                val activityCmpName =
                    ComponentName(appInfo.packageName, activityInfo.activityInfo.name)
                val key =
                    String.format("%s/%s", appInfo.packageName, activityInfo.activityInfo.name)
                if (!supportedPlayers.contains(key)) {
                    val label =
                        requireContext().packageManager.getApplicationLabel(appInfo).toString()
                    var iconBmp: Bitmap? = null
                    try {
                        val drawable =
                            requireContext().packageManager.getActivityIcon(activityCmpName)
                        iconBmp = drawable.toBitmap()
                    } catch (e: PackageManager.NameNotFoundException) {
                    }

                    playerModels.add(MusicPlayerViewModel().apply {
                        appLabel = label
                        packageName = appInfo.packageName
                        activityName = activityInfo.activityInfo.name
                        bitmapIcon = iconBmp
                    })
                    supportedPlayers.add(key)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            playerAdapter.updateItems(playerModels)
            showProgressBar(false)
            if (playerModels.isNullOrEmpty()) {
                binding.noplayersView.visibility = View.VISIBLE
                binding.playerList.visibility = View.GONE
            } else {
                binding.noplayersView.visibility = View.GONE
                binding.playerList.visibility = View.VISIBLE
                binding.playerList.requestFocus()
            }
        }
    }
}