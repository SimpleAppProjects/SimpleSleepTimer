package com.thewizrd.simplesleeptimer.wearable

import android.os.Bundle
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.thewizrd.shared_resources.utils.ContextUtils.getAttrColor
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.databinding.ActivityWearpermissionsBinding
import com.thewizrd.simplesleeptimer.preferences.Settings
import com.thewizrd.simplesleeptimer.utils.ActivityUtils.setTransparentWindow

class WearPermissionsActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "WearPermissionsActivity"
    }

    private lateinit var binding: ActivityWearpermissionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        window.enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        window.allowEnterTransitionOverlap = true

        super.onCreate(savedInstanceState)
        binding = ActivityWearpermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)

        val backgroundColor = getAttrColor(android.R.attr.colorBackground)
        val surfaceColor = getAttrColor(R.attr.colorSurface)
        window.setTransparentWindow(backgroundColor, surfaceColor, surfaceColor)
        //window.setFullScreen(getOrientation() == Configuration.ORIENTATION_PORTRAIT)

        binding.bridgeTimerToggle.isChecked = Settings.isBridgeTimerEnabled()
        binding.bridgeTimerToggle.setOnCheckedChangeListener { _, isChecked ->
            Settings.setBridgeTimerEnabled(isChecked)
        }

        binding.bridgeTimerPref.setOnClickListener {
            binding.bridgeTimerToggle.toggle()
        }
    }
}