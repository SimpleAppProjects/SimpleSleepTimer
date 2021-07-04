package com.thewizrd.simplesleeptimer

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Bundle
import android.service.media.MediaBrowserService
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.thewizrd.shared_resources.helpers.RecyclerOnClickListenerInterface
import com.thewizrd.shared_resources.viewmodels.MusicPlayerViewModel
import com.thewizrd.simplesleeptimer.adapters.PlayerListAdapter
import com.thewizrd.simplesleeptimer.databinding.FragmentMusicPlayersBinding
import com.thewizrd.simplesleeptimer.preferences.Settings
import com.thewizrd.simplesleeptimer.utils.ActivityUtils
import com.thewizrd.simplesleeptimer.wearable.WearableWorker
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MusicPlayersFragment : Fragment(), BottomSheetCallbackInterface {
    private lateinit var binding: FragmentMusicPlayersBinding
    private lateinit var playerAdapter: PlayerListAdapter
    private lateinit var mBottomSheetBehavior: BottomSheetBehavior<View>
    private var windowInsetTop = 0

    companion object {
        private val _toolbarHeight =
            ActivityUtils.getAttrDimension(
                App.instance.appContext,
                android.R.attr.actionBarSize
            )
    }

    private val toolbarHeight: Int
        get() = _toolbarHeight + windowInsetTop

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMusicPlayersBinding.inflate(inflater, container, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            windowInsetTop = insets.systemWindowInsetTop
            insets
        }

        binding.navigationIcon.setOnClickListener {
            mBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        playerAdapter = PlayerListAdapter()
        binding.playersList.layoutManager = LinearLayoutManager(context)
        binding.playersList.adapter = playerAdapter
        binding.playersList.setHasFixedSize(false)
        binding.playersList.addItemDecoration(
            DividerItemDecoration(
                context,
                DividerItemDecoration.VERTICAL
            )
        )

        playerAdapter.setOnClickListener(object : RecyclerOnClickListenerInterface {
            override fun onClick(view: View, position: Int) {
                val selectedItem = playerAdapter.getSelectedItem()
                if (selectedItem?.bitmapIcon != null) {
                    binding.musicplayerIcon.setImageBitmap(selectedItem.bitmapIcon)
                    binding.musicplayerText.text = selectedItem.appLabel
                    ImageViewCompat.setImageTintList(binding.musicplayerIcon, null)
                } else {
                    binding.musicplayerIcon.setImageResource(R.drawable.ic_music_note)
                    binding.musicplayerText.setText(R.string.title_audioplayer)
                    ImageViewCompat.setImageTintList(
                        binding.musicplayerIcon,
                        ColorStateList.valueOf(
                            ActivityUtils.getAttrValue(
                                requireContext(),
                                R.attr.colorAccent
                            )
                        )
                    )
                }

                WearableWorker.sendSelectedAudioPlayer(requireContext())
            }
        })

        binding.musicplayerText.isSelected = true

        return binding.root
    }

    override fun onBottomSheetBehaviorInitialized(bottomSheet: View) {
        mBottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        mBottomSheetBehavior.peekHeight = binding.musicplayerText.measuredHeight
        binding.musicplayerText.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            mBottomSheetBehavior.peekHeight = v.measuredHeight
        }
    }

    override fun onSlide(bottomSheet: View, slideOffset: Float) {
        when {
            slideOffset >= 1.0f -> {
                binding.peekGroup.alpha = 0f
                binding.peekGroup.layoutParams.height = 0
                binding.peekGroup.visibility = View.GONE

                binding.bottomSheetAppbar.layoutParams.height = toolbarHeight
                binding.bottomSheetAppbar.alpha = 1f
                binding.bottomSheetAppbar.visibility = View.VISIBLE
            }
            slideOffset <= 0.01f -> {
                binding.peekGroup.alpha = 1.0f
                binding.peekGroup.visibility = View.VISIBLE

                binding.bottomSheetAppbar.layoutParams.height = 0
                binding.bottomSheetAppbar.alpha = 0f
                binding.bottomSheetAppbar.visibility = View.GONE
            }
            slideOffset >= 0.50f -> {
                binding.peekGroup.alpha = 0f
                binding.peekGroup.layoutParams.height = 0
                binding.peekGroup.visibility = View.GONE

                binding.bottomSheetAppbar.layoutParams.height =
                    (toolbarHeight * slideOffset * 2f).roundToInt().coerceAtMost(toolbarHeight)
                binding.bottomSheetAppbar.alpha = ((slideOffset - 0.5f) * 2f).coerceAtMost(1f)
                binding.bottomSheetAppbar.visibility = View.VISIBLE
            }
            slideOffset > 0.01f -> {
                binding.peekGroup.alpha = (1 - (slideOffset * 2f)).coerceAtLeast(0f)
                binding.peekGroup.layoutParams.height =
                    (toolbarHeight * (1 - (slideOffset * 2f))).roundToInt()
                        .coerceAtLeast((toolbarHeight * 0.5).roundToInt())
                binding.peekGroup.visibility = View.VISIBLE

                binding.bottomSheetAppbar.layoutParams.height =
                    (toolbarHeight * slideOffset).roundToInt().coerceAtMost(toolbarHeight)
                binding.bottomSheetAppbar.alpha = 0f
                binding.bottomSheetAppbar.visibility = View.VISIBLE
            }
        }

        binding.bottomSheetAppbar.requestLayout()
    }

    override fun onStateChanged(bottomSheet: View, newState: Int) {
        // No-op
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

            if (playerPref != null && model is MusicPlayerViewModel && model.bitmapIcon != null) {
                binding.musicplayerIcon.setImageBitmap(model.bitmapIcon)
                binding.musicplayerText.text = model.appLabel
                ImageViewCompat.setImageTintList(binding.musicplayerIcon, null)
            } else {
                Settings.setMusicPlayer(null)
                binding.musicplayerIcon.setImageResource(R.drawable.ic_music_note)
                binding.musicplayerText.setText(R.string.title_audioplayer)
                ImageViewCompat.setImageTintList(
                    binding.musicplayerIcon,
                    ColorStateList.valueOf(
                        ActivityUtils.getAttrValue(
                            requireContext(),
                            R.attr.colorAccent
                        )
                    )
                )
            }
        }
    }
}