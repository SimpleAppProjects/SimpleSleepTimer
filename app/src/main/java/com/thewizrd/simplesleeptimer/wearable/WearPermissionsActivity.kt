package com.thewizrd.simplesleeptimer.wearable

import android.annotation.TargetApi
import android.bluetooth.BluetoothDevice
import android.companion.*
import android.content.*
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Parcelable
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.simplesleeptimer.BuildConfig
import com.thewizrd.simplesleeptimer.R
import com.thewizrd.simplesleeptimer.databinding.ActivityWearpermissionsBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class WearPermissionsActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "WearPermissionsActivity"
        private const val SELECT_DEVICE_REQUEST_CODE = 42
    }

    private lateinit var binding: ActivityWearpermissionsBinding
    private var timer: CountDownTimer? = null

    private lateinit var mWearManager: WearableManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWearpermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            if (deviceName.isNullOrBlank()) return@launch

            val deviceManager =
                getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager

            for (assoc in deviceManager.associations) {
                deviceManager.disassociate(assoc)
            }
            updatePairPermText(false)

            val request = AssociationRequest.Builder()
                .addDeviceFilter(
                    BluetoothDeviceFilter.Builder()
                        .setNamePattern(Pattern.compile("$deviceName.*", Pattern.DOTALL))
                        .build()
                )
                .addDeviceFilter(
                    BluetoothLeDeviceFilter.Builder()
                        .setNamePattern(Pattern.compile("$deviceName.*", Pattern.DOTALL))
                        .build()
                )
                .addDeviceFilter(
                    WifiDeviceFilter.Builder()
                        .setNamePattern(Pattern.compile("$deviceName.*", Pattern.DOTALL))
                        .build()
                )
                .apply {
                    if (BuildConfig.DEBUG) {
                        addDeviceFilter(
                            WifiDeviceFilter.Builder()
                                .setNamePattern(Pattern.compile(".*", Pattern.DOTALL))
                                .build()
                        )
                    }
                }
                .setSingleDevice(true)
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
            SELECT_DEVICE_REQUEST_CODE -> if (data != null && Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
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