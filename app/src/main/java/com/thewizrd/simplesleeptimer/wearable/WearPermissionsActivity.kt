package com.thewizrd.simplesleeptimer.wearable

import android.annotation.TargetApi
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanFilter
import android.companion.*
import android.content.*
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
        private const val SELECT_DEVICE_REQUEST_CODE = 42

        // WearOS device filter
        private val BLE_WEAR_MATCH_DATA = byteArrayOf(0, 20)
        private val BLE_WEAR_MATCH_DATA_LEGACY = byteArrayOf(0, 19)
        private val BLE_WEAR_MATCH_MASK = byteArrayOf(0, -1)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

    // Android Q+
    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (WearableDataListenerService.ACTION_GETCONNECTEDNODE == intent.action) {
                timer?.cancel()
                binding.companionPairProgress.visibility = View.GONE
                Log.i(TAG, "node received")
                pairDevice(intent.getStringExtra(WearableDataListenerService.EXTRA_NODEDEVICENAME))
                LocalBroadcastManager.getInstance(context)
                    .unregisterReceiver(this)
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun pairDevice(deviceName: String?) {
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
                if (!deviceName.isNullOrBlank()) {
                    addDeviceFilter(
                        BluetoothDeviceFilter.Builder()
                            .setNamePattern(Pattern.compile(".*$deviceName.*", Pattern.DOTALL))
                            .build()
                    )
                    addDeviceFilter(
                        WifiDeviceFilter.Builder()
                            .setNamePattern(Pattern.compile(".*$deviceName.*", Pattern.DOTALL))
                            .build()
                    )
                    addDeviceFilter(
                        BluetoothLeDeviceFilter.Builder()
                            .setNamePattern(Pattern.compile(".*$deviceName.*", Pattern.DOTALL))
                            .build()
                    )
                }

                // https://stackoverflow.com/questions/66222673/how-to-filter-nearby-bluetooth-devices-by-type
                addDeviceFilter(
                    BluetoothLeDeviceFilter.Builder()
                        .setScanFilter(
                            ScanFilter.Builder()
                                .setManufacturerData(
                                    0xE0,
                                    BLE_WEAR_MATCH_DATA_LEGACY,
                                    BLE_WEAR_MATCH_MASK
                                )
                                .build()
                        )
                        .setNamePattern(Pattern.compile(".*", Pattern.DOTALL))
                        .build()
                )
                if (BuildConfig.DEBUG) {
                    addDeviceFilter(
                        WifiDeviceFilter.Builder()
                            .setNamePattern(Pattern.compile(".*", Pattern.DOTALL))
                            .build()
                    )
                }
            }
                .setSingleDevice(false)
                .build()

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
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled) {
                    bluetoothAdapter.enable()
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

                    override fun onFailure(error: CharSequence) {
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
}