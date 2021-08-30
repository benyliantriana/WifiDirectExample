package com.example.wifidirectexample

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ChannelListener
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), ChannelListener, DeviceListFragment.DeviceActionListener {
    private var manager: WifiP2pManager? = null
    private var isWifiP2pEnabled = false
    private var retryChannel = false
    private val intentFilter = IntentFilter()
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        initIntentFilter()
        checkIfWifiDirectSupported()
        checkLocationPermission()
    }

    private fun initIntentFilter() {
        intentFilter.apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
    }

    private fun checkIfWifiDirectSupported() {
        if (!isWifiDirectSupported()) {
            finish()
        }
    }

    private fun checkLocationPermission() {
        requestPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION
        )
    }

    private fun checkStoragePermission() {
        requestPermissions(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
            PERMISSION_REQUEST_CODE_STORAGE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                    Manifest.permission.MANAGE_DOCUMENTS
                ),
                PERMISSION_REQUEST_CODE_STORAGE
            )
        }
    }

    fun setIsWifiP2pEnabled(isWifiP2pEnabled: Boolean) {
        this.isWifiP2pEnabled = isWifiP2pEnabled
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION -> if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Fine location permission is not granted!")
                finish()
            } else {
                checkStoragePermission()
            }
        }
    }

    private fun isWifiDirectSupported(): Boolean {
        // Device capability definition check
        if (!hasFeatureWifiDirect()) {
            Log.e(TAG, "Wi-Fi Direct is not supported by this device.")
            return false
        }

        // Hardware capability check
        if (!isP2PSupported()) {
            Log.e(TAG, "Wi-Fi Direct is not supported by the hardware or Wi-Fi is off.")
            return false
        }

        if (!isWifiDirectSystemServiceAvailable()) {
            Log.e(TAG, "Cannot get Wi-Fi Direct system service.")
            return false
        }

        if (!isInitiateWifiDirectSuccess()) {
            Log.e(TAG, "Cannot initialize Wi-Fi Direct.")
            return false
        }

        return true
    }

    private fun hasFeatureWifiDirect(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
    }

    private fun isP2PSupported(): Boolean {
        val wifiManager: WifiManager =
            applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        if (!wifiManager.isP2pSupported) {
            return false
        }
        return true
    }

    private fun isWifiDirectSystemServiceAvailable(): Boolean {
        manager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        if (manager == null) {
            return false
        }
        return true
    }

    private fun isInitiateWifiDirectSuccess(): Boolean {
        channel = manager?.initialize(this, mainLooper, null)
        if (channel == null) {
            return false
        }
        return true
    }

    public override fun onResume() {
        super.onResume()
        receiver = WifiDirectBroadcasterReceiver(manager, channel, this)
        registerReceiver(receiver, intentFilter)
    }

    public override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    fun resetData() {
        val fragmentList =
            supportFragmentManager.findFragmentById(R.id.frag_list) as DeviceListFragment
        val fragmentDetails =
            supportFragmentManager.findFragmentById(R.id.frag_detail) as DeviceDetailFragment
        fragmentList.clearPeers()
        fragmentDetails.resetViews()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_items, menu)
        return true
    }

    @SuppressLint("MissingPermission")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.atn_direct_enable -> {
                if (manager != null && channel != null) {
                    startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                } else {
                    Log.e(TAG, "channel or manager is null")
                }
                true
            }
            R.id.atn_direct_discover -> {
                if (!isWifiP2pEnabled) {
                    showToast(getString(R.string.p2p_off_warning))
                    return true
                }
                val fragment =
                    supportFragmentManager.findFragmentById(R.id.frag_list) as DeviceListFragment
                fragment.onInitiateDiscovery()
                manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        showToast("Discovery Initiated")
                    }

                    override fun onFailure(reasonCode: Int) {
                        showToast("Discovery Failed: $reasonCode")
                    }
                })
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(
            this@MainActivity, message,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun showDetails(device: WifiP2pDevice?) {
        val fragment =
            supportFragmentManager.findFragmentById(R.id.frag_detail) as DeviceDetailFragment
        device?.let {
            fragment.showDetails(it)
        }
    }

    @SuppressLint("MissingPermission")
    override fun connect(config: WifiP2pConfig?) {
        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            override fun onFailure(reason: Int) {
                showToast("Connect failed. Retry.")
            }
        })
    }

    override fun disconnect() {
        val fragment =
            supportFragmentManager.findFragmentById(R.id.frag_detail) as DeviceDetailFragment
        fragment.resetViews()
        manager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onFailure(reasonCode: Int) {
                Log.d(
                    TAG,
                    "Disconnect failed. Reason :$reasonCode"
                )
            }

            override fun onSuccess() {
                fragment.view?.visibility = View.GONE
            }
        })
    }

    override fun onChannelDisconnected() {
        if (manager != null && !retryChannel) {
            showToast("Channel lost. Trying again")
            resetData()
            retryChannel = true
            manager?.initialize(this, mainLooper, this)
        } else {
            showToast("Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.")
        }
    }

    override fun cancelDisconnect() {
        if (manager != null) {
            val fragment =
                supportFragmentManager.findFragmentById(R.id.frag_list) as DeviceListFragment
            if (fragment.device == null || fragment.device?.status == WifiP2pDevice.CONNECTED) {
                disconnect()
            } else if (fragment.device?.status == WifiP2pDevice.AVAILABLE
                || fragment.device?.status == WifiP2pDevice.INVITED
            ) {
                manager?.cancelConnect(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        showToast("Aborting connection")
                    }

                    override fun onFailure(reasonCode: Int) {
                        showToast("Connect abort request failed. Reason Code: $reasonCode")
                    }
                })
            }
        }
    }

    companion object {
        const val TAG = "wifidirectdemo"
        private const val PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION = 1001
        private const val PERMISSION_REQUEST_CODE_STORAGE = 1002
    }
}