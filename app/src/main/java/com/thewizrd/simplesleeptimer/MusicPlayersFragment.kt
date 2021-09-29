package com.thewizrd.simplesleeptimer

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.service.media.MediaBrowserService
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.thewizrd.shared_resources.helpers.RecyclerOnClickListenerInterface
import com.thewizrd.shared_resources.helpers.SimpleRecyclerViewAdapterObserver
import com.thewizrd.shared_resources.viewmodels.MusicPlayerViewModel
import com.thewizrd.simplesleeptimer.adapters.PlayerListAdapter
import com.thewizrd.simplesleeptimer.databinding.FragmentMusicPlayersBinding
import com.thewizrd.simplesleeptimer.preferences.Settings
import com.thewizrd.simplesleeptimer.wearable.WearableWorker
import kotlinx.coroutines.launch

class MusicPlayersFragment : Fragment() {
    private lateinit var binding: FragmentMusicPlayersBinding
    private lateinit var playerAdapter: PlayerListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMusicPlayersBinding.inflate(inflater, container, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.playersList) { v, insets ->
            v.updatePadding(
                bottom = insets.systemWindowInsetBottom
            )
            insets
        }

        binding.bottomSheetToolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }

        playerAdapter = PlayerListAdapter()
        binding.playersList.layoutManager = LinearLayoutManager(context)
        binding.playersList.adapter = playerAdapter
        binding.playersList.setHasFixedSize(true)

        playerAdapter.setOnClickListener(object : RecyclerOnClickListenerInterface {
            override fun onClick(view: View, position: Int) {
                WearableWorker.sendSelectedAudioPlayer(requireContext())
            }
        })

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playerAdapter.registerAdapterDataObserver(object : SimpleRecyclerViewAdapterObserver() {
            override fun onChanged() {
                playerAdapter.unregisterAdapterDataObserver(this)
                binding.progressBar.hide()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        updateSupportedMusicPlayers()
    }

    private fun updateSupportedMusicPlayers() {
        val supportedPlayers = ArrayList<String>()
        val playerModels = ArrayList<MusicPlayerViewModel>()

        fun addPlayerInfo(appInfo: ApplicationInfo) {
            val launchIntent =
                requireContext().packageManager.getLaunchIntentForPackage(appInfo.packageName)
            if (launchIntent != null) {
                val activityInfo = requireContext().packageManager.resolveActivity(
                    launchIntent,
                    PackageManager.MATCH_DEFAULT_ONLY
                )
                    ?: return
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

        /* Media Button Receivers */
        val infos = requireContext().packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_MEDIA_BUTTON), PackageManager.GET_RESOLVED_FILTER
        )

        for (info in infos) {
            val appInfo = info.activityInfo.applicationInfo
            addPlayerInfo(appInfo)
        }

        /* MediaBrowser services */
        val mediaBrowserInfos = requireContext().packageManager.queryIntentServices(
            Intent(MediaBrowserService.SERVICE_INTERFACE),
            PackageManager.GET_RESOLVED_FILTER
        )

        for (info in mediaBrowserInfos) {
            val appInfo = info.serviceInfo.applicationInfo
            addPlayerInfo(appInfo)
        }

        // Sort result
        playerModels.sortBy { it.appLabel?.lowercase() }

        val playerPref = Settings.getMusicPlayer()
        val model = playerModels.find { i -> i.key != null && i.key == playerPref }

        viewLifecycleOwner.lifecycleScope.launch {
            playerAdapter.updateItems(playerModels)

            if (playerPref == null || model !is MusicPlayerViewModel || model.bitmapIcon == null) {
                Settings.setMusicPlayer(null)
            }
        }
    }
}