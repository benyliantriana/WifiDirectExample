package com.example.wifidirectexample

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.PeerListListener
import android.os.Parcelable
import android.util.Log
import androidx.core.app.ActivityCompat

class WifiDirectBroadcasterReceiver(
    private val manager: WifiP2pManager?, private val channel: WifiP2pManager.Channel?,
    private val activity: MainActivity
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                handleUpdateStateP2P(intent)
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                handleUpdateP2PPeers()
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                handleUpdateConnectionChanged(intent)
            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                handleUpdateWifiDevice(intent)
            }
        }
    }

    private fun handleUpdateStateP2P(intent: Intent) {
        // UI update to indicate wifi p2p status.
        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
        if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
            // Wifi Direct mode is enabled
            activity.setIsWifiP2pEnabled(true)
        } else {
            activity.setIsWifiP2pEnabled(false)
            activity.resetData()
        }
        Log.d(
            MainActivity.TAG,
            "P2P state changed - $state"
        )
    }

    private fun handleUpdateP2PPeers() {
        if (ActivityCompat.checkSelfPermission(
                this.activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager?.requestPeers(
            channel, activity.supportFragmentManager
                .findFragmentById(R.id.frag_list) as PeerListListener
        )
        Log.d(MainActivity.TAG, "P2P peers changed")
    }

    private fun handleUpdateConnectionChanged(intent: Intent) {
        if (manager == null) {
            return
        }
        val networkInfo = intent
            .getParcelableExtra<Parcelable>(WifiP2pManager.EXTRA_NETWORK_INFO) as NetworkInfo?
        if (networkInfo?.isConnected == true) {
            val fragment =
                activity.supportFragmentManager.findFragmentById(R.id.frag_detail) as DeviceDetailFragment
            manager.requestConnectionInfo(channel, fragment)
        } else {
            activity.resetData()
        }
    }

    private fun handleUpdateWifiDevice(intent: Intent) {
        val fragment = activity.supportFragmentManager
            .findFragmentById(R.id.frag_list) as DeviceListFragment
        fragment.updateThisDevice(
            (intent.getParcelableExtra<Parcelable>(
                WifiP2pManager.EXTRA_WIFI_P2P_DEVICE
            ) as WifiP2pDevice?)!!
        )
    }
}