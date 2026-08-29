package com.thewizrd.simplesleeptimer.wearable

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import androidx.core.view.isVisible
import com.google.android.material.color.DynamicColors
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.thewizrd.shared_resources.services.BaseTimerService
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.databinding.ActivityWearpermissionsBinding
import com.thewizrd.simplesleeptimer.preferences.Settings

class WearPermissionsActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "WearPermissionsActivity"

        private const val CORNERS_FULL = 0
        private const val CORNERS_TOP = 1
        private const val CORNERS_CENTER = 2
        private const val CORNERS_BOTTOM = 3
    }

    private lateinit var binding: ActivityWearpermissionsBinding

    private lateinit var permissionRequestLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        window.enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        window.allowEnterTransitionOverlap = true

        super.onCreate(savedInstanceState)

        permissionRequestLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

        DynamicColors.applyToActivityIfAvailable(this)

        binding = ActivityWearpermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.alarmPermPref.isVisible = !BaseTimerService.checkExactAlarmsPermission(this)

            binding.alarmPermPref.setOnClickListener {
                runCatching {
                    startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }.onFailure { t ->
                    Logger.error(TAG, t, "Error")
                }
            }
        } else {
            binding.alarmPermPref.isVisible = false
        }

        binding.notifPref.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
                permissionRequestLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        binding.notifPref.isVisible =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()

        binding.bridgeTimerToggle.isChecked = Settings.isBridgeTimerEnabled()
        binding.bridgeTimerToggle.setOnCheckedChangeListener { _, isChecked ->
            Settings.setBridgeTimerEnabled(isChecked)
        }

        binding.bridgeTimerPref.setOnClickListener {
            binding.bridgeTimerToggle.toggle()
        }

        binding.scrollView.children.firstOrNull().let { root ->
            val parent = root as ViewGroup

            parent.viewTreeObserver.addOnGlobalLayoutListener {
                updateRoundedBackground(parent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissions()
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun updatePermissions() {
        binding.bridgeTimerToggle.isChecked = Settings.isBridgeTimerEnabled()
        binding.alarmPermPref.isVisible =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !BaseTimerService.checkExactAlarmsPermission(
                this
            )
        binding.alarmPermPrefSummary.setTextColor(
            getTextColor(
                binding.alarmPermPrefSummary.context,
                !binding.alarmPermPref.isVisible
            )
        )
        binding.notifPref.isVisible = !hasNotificationPermission()
        binding.notifPrefSummary.setTextColor(
            getTextColor(
                binding.notifPrefSummary.context,
                !binding.notifPref.isVisible
            )
        )
    }

    @ColorInt
    private fun getTextColor(context: Context, enabled: Boolean): Int {
        return when (enabled) {
            true -> if ((context.resources.configuration.uiMode and UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES) {
                Color.GREEN
            } else {
                ColorUtils.blendARGB(Color.GREEN, Color.BLACK, 0.25f)
            }

            false -> if ((context.resources.configuration.uiMode and UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES) {
                ColorUtils.blendARGB(Color.RED, Color.WHITE, 0.25f)
            } else {
                Color.RED
            }
        }
    }

    private fun updateRoundedBackground(parent: ViewGroup) {
        val settingsPreferences =
            parent.children.filter { it.tag == "settings" && it.isVisible }.toList()

        settingsPreferences.forEachIndexed { index, view ->
            val cornerType = when {
                settingsPreferences.size <= 1 -> CORNERS_FULL
                index == 0 -> CORNERS_TOP
                index == settingsPreferences.size - 1 -> CORNERS_BOTTOM
                else -> CORNERS_CENTER
            }

            when (cornerType) {
                CORNERS_FULL -> view.setBackgroundResource(R.drawable.preference_round_background)
                CORNERS_TOP -> view.setBackgroundResource(R.drawable.preference_round_background_top)
                CORNERS_BOTTOM -> view.setBackgroundResource(R.drawable.preference_round_background_bottom)
                CORNERS_CENTER -> view.setBackgroundResource(R.drawable.preference_round_background_center)
            }
        }
    }
}