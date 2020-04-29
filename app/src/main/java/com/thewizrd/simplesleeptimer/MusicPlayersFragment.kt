package com.thewizrd.simplesleeptimer

import android.app.Dialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.thewizrd.simplesleeptimer.adapters.PlayerListAdapter
import com.thewizrd.simplesleeptimer.databinding.FragmentMusicPlayersBinding
import com.thewizrd.simplesleeptimer.helpers.RecyclerOnClickListenerInterface
import com.thewizrd.simplesleeptimer.viewmodels.MusicPlayerViewModel

class MusicPlayersFragment : BottomSheetDialogFragment() {
    private lateinit var binding: FragmentMusicPlayersBinding
    private lateinit var playerAdapter: PlayerListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentMusicPlayersBinding.inflate(inflater, container, false)

        playerAdapter = PlayerListAdapter()
        binding.playersList.layoutManager = LinearLayoutManager(context)
        binding.playersList.adapter = playerAdapter

        binding.navigationIcon.setOnClickListener {
            this.dismiss()
        }
        playerAdapter.setOnClickListener(object : RecyclerOnClickListenerInterface {
            override fun onClick(view: View, position: Int) {
                this@MusicPlayersFragment.dismiss()
            }
        })

        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.skipCollapsed = true
        dialog.behavior.isFitToContents = false
        dialog.behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            private val toolbarHeight =
                dialog.context.resources.getDimensionPixelSize(R.dimen.mtrl_toolbar_default_height)

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (slideOffset >= 1.0f) {
                    binding.appBarLayout.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    binding.topAppBar.visibility = View.VISIBLE
                    binding.scrollView.layoutParams.height =
                        activity?.window?.decorView?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
                } else if (slideOffset <= 0) {
                    binding.appBarLayout.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    binding.topAppBar.visibility = View.GONE
                } else if (slideOffset > 0) {
                    binding.appBarLayout.layoutParams.height = (toolbarHeight * slideOffset).toInt()
                    binding.topAppBar.visibility = View.INVISIBLE
                    binding.scrollView.layoutParams.height =
                        activity?.window?.decorView?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
                }

                binding.appBarLayout.requestLayout()
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    binding.appBarLayout.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    binding.topAppBar.visibility = View.VISIBLE
                    binding.scrollView.layoutParams.height =
                        activity?.window?.decorView?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
                } else if (newState == BottomSheetBehavior.STATE_HALF_EXPANDED) {
                    binding.scrollView.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    binding.topAppBar.visibility = View.GONE
                }
            }
        })
        dialog.behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        dialog.setOnShowListener {
            if (dialog.context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                binding.scrollView.layoutParams.height = dialog.window!!.decorView.height
                binding.topAppBar.visibility = View.VISIBLE
                dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                binding.scrollView.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                binding.topAppBar.visibility = View.GONE
                dialog.behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
            }
        }
        return dialog
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

        playerAdapter.updateItems(supportedPlayers.values.toList())
    }
}