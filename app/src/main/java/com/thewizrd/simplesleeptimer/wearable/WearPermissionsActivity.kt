package com.thewizrd.simplesleeptimer.wearable

import android.os.Bundle
import android.view.Window
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.thewizrd.simplesleeptimer.databinding.ActivityWearpermissionsBinding
import com.thewizrd.simplesleeptimer.preferences.Settings

class WearPermissionsActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "WearPermissionsActivity"
    }

    private lateinit var binding: ActivityWearpermissionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        window.enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        window.allowEnterTransitionOverlap = true

        super.onCreate(savedInstanceState)

        DynamicColors.applyToActivityIfAvailable(this)

        binding = ActivityWearpermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bridgeTimerToggle.isChecked = Settings.isBridgeTimerEnabled()
        binding.bridgeTimerToggle.setOnCheckedChangeListener { _, isChecked ->
            Settings.setBridgeTimerEnabled(isChecked)
        }

        binding.bridgeTimerPref.setOnClickListener {
            binding.bridgeTimerToggle.toggle()
        }
    }
}