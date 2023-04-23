package com.thewizrd.simplesleeptimer.wearable

import android.Manifest
import android.annotation.TargetApi
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Parcelable
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.ContextUtils.getAttrColor
import com.thewizrd.simplesleeptimer.BuildConfig
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.databinding.ActivityWearpermissionsBinding
import com.thewizrd.simplesleeptimer.preferences.Settings
import com.thewizrd.simplesleeptimer.utils.ActivityUtils.setTransparentWindow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class WearPermissionsActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "WearPermissionsActivity"
        private const val BTCONNECT_REQCODE = 0
        private const val SELECT_DEVICE_REQUEST_CODE = 42
    }

    private lateinit var binding: ActivityWearpermissionsBinding
    private var timer: CountDownTimer? = null

    private lateinit var mWearManager: WearableManager

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

        binding.companionPairPref.setOnClickListener {
            startDevicePairing()
        }

        binding.bridgeTimerToggle.isChecked = Settings.isBridgeTimerEnabled()
        binding.bridgeTimerToggle.setOnCheckedChangeListener { _, isChecked ->
            Settings.setBridgeTimerEnabled(isChecked)
        }

        binding.bridgeTimerPref.setOnClickListener {
            binding.bridgeTimerToggle.toggle()
        }
    }

    override fun onPause() {
        mWearManager.unregister()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver)
        super.onPause()
    }

    private fun isBluetoothConnectPermGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun startDevicePairing() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!isBluetoothConnectPermGranted()) {
                    requestPermissions(
                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                        BTCONNECT_REQCODE
                    )
                    return
                }
            }

            LocalBroadcastManager.getInstance(this)
                .registerReceiver(
                    mReceiver,
                    IntentFilter(WearableDataListenerService.ACTION_GETCONNECTEDNODE)
                )
            if (timer == null) {
                timer = object : CountDownTimer(5000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {}
                    override fun onFinish() {
                        lifecycleScope.launch {
                            Toast.makeText(
                                this@WearPermissionsActivity,
                                R.string.message_watchbttimeout,
                                Toast.LENGTH_LONG
                            ).show()
                            binding.companionPairProgress.visibility = View.GONE
                            Log.i(TAG, "BT Request Timeout")
                            // Device not found showing all
                            pairDevice()
                        }
                    }
                }
            }
            timer?.start()
            binding.companionPairProgress.visibility = View.VISIBLE
            lifecycleScope.launch {
                mWearManager.sendMessage(null, WearableHelper.PingPath, null)
                mWearManager.sendMessage(null, WearableHelper.BtDiscoverPath, null)
            }
            Log.i(TAG, "ACTION_REQUESTBTDISCOVERABLE")
        }
    }

    // Android Q+
    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (WearableDataListenerService.ACTION_GETCONNECTEDNODE == intent.action) {
                timer?.cancel()
                binding.companionPairProgress.visibility = View.GONE
                Log.i(TAG, "node received")
                pairDevice()
                LocalBroadcastManager.getInstance(context)
                    .unregisterReceiver(this)
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun pairDevice() {
        lifecycleScope.launch {
            val deviceManager =
                getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager

            for (assoc in deviceManager.associations) {
                if (assoc != null) {
                    runCatching {
                        deviceManager.disassociate(assoc)
                    }.onFailure {
                        Log.e(TAG, "Error removing association", it)
                    }
                }
            }
            updatePairPermText(false)

            val request = AssociationRequest.Builder().apply {
                if (BuildConfig.DEBUG) {
                    addDeviceFilter(
                        BluetoothDeviceFilter.Builder()
                            .setNamePattern(Pattern.compile(".*", Pattern.DOTALL))
                            .build()
                    )
                    addDeviceFilter(
                        WifiDeviceFilter.Builder()
                            .setNamePattern(Pattern.compile(".*", Pattern.DOTALL))
                            .build()
                    )
                    addDeviceFilter(
                        BluetoothLeDeviceFilter.Builder()
                            .setNamePattern(Pattern.compile(".*", Pattern.DOTALL))
                            .build()
                    )
                } else {
                    addDeviceFilter(
                        BluetoothDeviceFilter.Builder()
                            .setNamePattern(Pattern.compile(".*", Pattern.DOTALL))
                            .build()
                    )
                    addDeviceFilter(
                        BluetoothLeDeviceFilter.Builder()
                            .setNamePattern(Pattern.compile(".*", Pattern.DOTALL))
                            .build()
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
                }
            }
                .setSingleDevice(false)
                .build()

            // Verify bluetooth permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isBluetoothConnectPermGranted()) {
                requestPermissions(
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    BTCONNECT_REQCODE
                )
                return@launch
            }

            Toast.makeText(
                this@WearPermissionsActivity,
                R.string.message_watchbtdiscover,
                Toast.LENGTH_LONG
            ).show()

            lifecycleScope.launch pairRequest@{
                delay(5000)
                if (!isActive) return@pairRequest

                Log.i(TAG, "sending pair request")
                // Enable Bluetooth to discover devices
                val btService = this@WearPermissionsActivity.applicationContext.getSystemService(
                    BluetoothManager::class.java
                )
                btService?.adapter?.run {
                    runCatching {
                        if (!this.isEnabled) {
                            this.enable()
                        }
                    }.onFailure {
                        Log.e(TAG, "Error", it)
                    }
                }
                deviceManager.associate(request, object : CompanionDeviceManager.Callback() {
                    override fun onDeviceFound(chooserLauncher: IntentSender) {
                        try {
                            startIntentSenderForResult(
                                chooserLauncher,
                                SELECT_DEVICE_REQUEST_CODE, null, 0, 0, 0, null
                            )
                        } catch (e: IntentSender.SendIntentException) {
                            Log.e(TAG, "Error", e)
                        }
                    }

                    override fun onFailure(error: CharSequence?) {
                        Log.e(TAG, "failed to find any devices; $error")
                        Toast.makeText(
                            this@WearPermissionsActivity,
                            R.string.message_nodevices_found,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }, null)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mWearManager = WearableManager(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val deviceManager =
                getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
            updatePairPermText(deviceManager.associations.isNotEmpty())
        } else {
            updatePairPermText(true)
        }
    }

    private fun updatePairPermText(enabled: Boolean) {
        binding.companionPairSummary.setText(if (enabled) R.string.permission_pairdevice_enabled else R.string.permission_pairdevice_disabled)
        binding.companionPairSummary.setTextColor(if (enabled) Color.GREEN else Color.RED)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            SELECT_DEVICE_REQUEST_CODE -> if (data != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val parcel =
                    data.getParcelableExtra<Parcelable>(CompanionDeviceManager.EXTRA_DEVICE)
                if (parcel is BluetoothDevice) {
                    if (parcel.bondState != BluetoothDevice.BOND_BONDED) {
                        parcel.createBond()
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val permGranted =
            grantResults.isNotEmpty() && !grantResults.contains(PackageManager.PERMISSION_DENIED)

        when (requestCode) {
            BTCONNECT_REQCODE -> {
                if (permGranted) {
                    startDevicePairing()
                } else {
                    Toast.makeText(this, R.string.error_permissiondenied, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }
}