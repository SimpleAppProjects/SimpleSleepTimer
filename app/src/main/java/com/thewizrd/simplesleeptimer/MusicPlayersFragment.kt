package com.thewizrd.simplesleeptimer

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.thewizrd.simplesleeptimer.adapters.PlayerListAdapter
import com.thewizrd.simplesleeptimer.databinding.FragmentMusicPlayersBinding
import com.thewizrd.simplesleeptimer.helpers.RecyclerOnClickListenerInterface
import com.thewizrd.simplesleeptimer.preferences.Settings
import com.thewizrd.simplesleeptimer.utils.ActivityUtils
import com.thewizrd.simplesleeptimer.viewmodels.MusicPlayerViewModel

class MusicPlayersFragment : Fragment(), BottomSheetCallbackInterface {
    private lateinit var binding: FragmentMusicPlayersBinding
    private lateinit var playerAdapter: PlayerListAdapter
    private lateinit var mBottomSheetBehavior: BottomSheetBehavior<View>

    companion object {
        private val toolbarHeight =
            ActivityUtils.dpToPx(App.getInstance().getAppContext(), 48f).toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentMusicPlayersBinding.inflate(inflater, container, false)

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
                mBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                val selectedItem = playerAdapter.getSelectedItem()
                if (selectedItem?.mBitmapIcon != null) {
                    binding.musicplayerIcon.setImageBitmap(selectedItem.mBitmapIcon)
                    binding.musicplayerText.text = selectedItem.mAppLabel
                    ImageViewCompat.setImageTintList(binding.musicplayerIcon, null)
                } else {
                    binding.musicplayerIcon.setImageResource(R.drawable.ic_music_note)
                    binding.musicplayerText.setText(R.string.text_music_player)
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
        if (slideOffset >= 1.0f) {
            binding.bottomSheetToolbar.layoutParams.height = toolbarHeight
            binding.bottomSheetToolbar.visibility = View.VISIBLE
        } else if (slideOffset <= 0.00f) {
            binding.bottomSheetToolbar.visibility = View.GONE
        } else if (slideOffset > 0.00f) {
            binding.bottomSheetToolbar.layoutParams.height =
                (toolbarHeight * slideOffset).toInt()
            binding.bottomSheetToolbar.visibility = View.INVISIBLE
        }

        if (slideOffset >= 1.0f) {
            val statBarColor =
                ContextCompat.getColor(requireContext(), R.color.colorSurface)
            binding.bottomSheetAppbar.setStatusBarForegroundColor(statBarColor)
        } else {
            binding.bottomSheetAppbar.statusBarForeground = null
        }

        if (slideOffset >= 0.95f) {
            val statBarColor =
                ContextCompat.getColor(requireContext(), R.color.colorSurface)
            ActivityUtils.setStatusBarColor(requireActivity().window, statBarColor, false)
        } else {
            binding.bottomSheetAppbar.statusBarForeground = null
            ActivityUtils.setStatusBarColor(requireActivity().window, 0, false)
        }

        if (slideOffset <= 0.00f) {
            binding.bottomSheetPeekgroup.visibility = View.VISIBLE
            binding.promptText.visibility = View.GONE
        } else {
            binding.bottomSheetPeekgroup.visibility = View.GONE
            binding.promptText.visibility = View.VISIBLE
        }

        binding.bottomSheetAppbar.requestLayout()
    }

    override fun onStateChanged(bottomSheet: View, newState: Int) {
        if (newState == BottomSheetBehavior.STATE_EXPANDED) {
            binding.bottomSheetToolbar.visibility = View.VISIBLE
        } else if (newState == BottomSheetBehavior.STATE_HALF_EXPANDED) {
            binding.bottomSheetToolbar.visibility = View.GONE
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        updateSupportedMusicPlayers()
    }

    override fun onPause() {
        super.onPause()
    }

    private fun updateSupportedMusicPlayers() {
        val infos = context?.packageManager?.queryBroadcastReceivers(
            Intent(Intent.ACTION_MEDIA_BUTTON), PackageManager.GET_RESOLVED_FILTER
        )

        val supportedPlayers = HashMap<String, MusicPlayerViewModel>()

        if (infos != null) {
            for (info in infos) {
                val appInfo = info.activityInfo.applicationInfo
                val launchIntent =
                    context?.packageManager?.getLaunchIntentForPackage(appInfo.packageName)
                if (launchIntent != null) {
                    val activityInfo = context?.packageManager?.resolveActivity(
                        launchIntent,
                        PackageManager.MATCH_DEFAULT_ONLY
                    )

                    if (activityInfo == null) continue

                    val activityCmpName =
                        ComponentName(appInfo.packageName, activityInfo.activityInfo.name)
                    val key =
                        String.format("%s/%s", appInfo.packageName, activityInfo.activityInfo.name)

                    val label = context?.packageManager?.getApplicationLabel(appInfo).toString()
                    var iconBmp: Bitmap? = null
                    try {
                        val drawable = context?.packageManager?.getActivityIcon(activityCmpName)
                        iconBmp = drawable?.toBitmap()
                    } catch (e: PackageManager.NameNotFoundException) {
                    }

                    if (!supportedPlayers.containsKey(key)) {
                        supportedPlayers[key] = MusicPlayerViewModel().apply {
                            mAppLabel = label
                            mPackageName = appInfo.packageName
                            mActivityName = activityInfo.activityInfo.name
                            mBitmapIcon = iconBmp
                        }
                    }
                }
            }
        }

        supportedPlayers.values.toList().also {
            playerAdapter.updateItems(it)

            val playerPref = Settings.getMusicPlayer()
            val model =
                it.find { i -> i.getKey() != null && i.getKey() == playerPref }

            if (playerPref != null && model is MusicPlayerViewModel && model.mBitmapIcon != null) {
                binding.musicplayerIcon.setImageBitmap(model.mBitmapIcon)
                binding.musicplayerText.text = model.mAppLabel
                ImageViewCompat.setImageTintList(binding.musicplayerIcon, null)
            } else {
                Settings.setMusicPlayer(null)
                binding.musicplayerIcon.setImageResource(R.drawable.ic_music_note)
                binding.musicplayerText.setText(R.string.text_music_player)
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