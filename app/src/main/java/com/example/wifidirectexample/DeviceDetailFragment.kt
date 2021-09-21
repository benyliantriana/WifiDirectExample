package com.example.wifidirectexample

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.content.Intent.createChooser
import android.net.Uri
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket


class DeviceDetailFragment : Fragment(), ConnectionInfoListener {
    private var mContentView: View? = null
    private var device: WifiP2pDevice? = null
    private var info: WifiP2pInfo? = null
    var progressDialog: ProgressDialog? = null
    var localIP: String? = null
    var clientIP: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        allowThreadPolicy()
    }

    private fun allowThreadPolicy() {
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
    }

    @SuppressLint("InflateParams")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mContentView = inflater.inflate(R.layout.device_detail, null)
        mContentView?.apply {
            findViewById<View>(R.id.btn_connect)?.setOnClickListener {
                pairingDevice()
            }

            findViewById<View>(R.id.btn_disconnect)?.setOnClickListener {
                (activity as DeviceListFragment.DeviceActionListener).disconnect()
            }

            findViewById<View>(R.id.btn_start_client)?.setOnClickListener {
                openFileZip()
            }
        }
        return mContentView
    }

    private fun pairingDevice() {
        val config = WifiP2pConfig()
        config.deviceAddress = device?.deviceAddress
        config.wps.setup = WpsInfo.PBC
        if (progressDialog != null && progressDialog?.isShowing == true) {
            progressDialog?.dismiss()
        }
        progressDialog = ProgressDialog.show(
            activity,
            "Press back to cancel",
            "Connecting to :" + device?.deviceAddress,
            true,
            true
        )
        (activity as DeviceListFragment.DeviceActionListener).connect(
            config
        )
    }

    private fun openFileZip() {
        val intent = Intent()
        intent.apply {
            type = "application/zip"
            action = Intent.ACTION_GET_CONTENT
        }
        startActivityForResult(
            createChooser(intent, "Select File Zip"),
            CHOOSE_FILE_RESULT_CODE
        )
    }

    @SuppressLint("SetTextI18n")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri = data?.data
        val statusText = mContentView?.findViewById<View>(R.id.status_text) as TextView
        statusText.text = "Sending: $uri"

        if (uri != null) {
            handlingUriFile(uri)
        }
    }

    private fun handlingUriFile(uri: Uri?) {
        val address: String? = if (localIP.equals(IP_SERVER)) {
            clientIP
        } else {
            IP_SERVER
        }

        if (address != null) {
            sendingFiles(uri, address)
        } else {
            Log.d(MainActivity.TAG, "address is null")
        }
    }

    private fun sendingFiles(uri: Uri?, address: String) {
        val serviceIntent = Intent(requireContext(), FileTransferService::class.java)
        serviceIntent.apply {
            action = FileTransferService.ACTION_SEND_FILE
            putExtra(FileTransferService.EXTRAS_FILE_PATH, uri.toString())
            putExtra(
                FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
                address
            )
            putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8988)
        }
        requireActivity().startService(serviceIntent)
    }

    @SuppressLint("SetTextI18n")
    override fun onConnectionInfoAvailable(info: WifiP2pInfo) {
        if (progressDialog != null && progressDialog?.isShowing == true) {
            progressDialog?.dismiss()
        }
        this.info = info
        this.view?.visibility = View.VISIBLE

        var view = mContentView?.findViewById<View>(R.id.group_owner) as TextView
        view.text =
            resources.getString(R.string.group_owner_text) + (if ((info.isGroupOwner)) resources.getString(
                R.string.yes
            ) else resources.getString(
                R.string.no
            ))

        view = mContentView?.findViewById<View>(R.id.device_info) as TextView
        view.text = "Group Owner IP - " + info.groupOwnerAddress.hostAddress

        if (info.isGroupOwner) {
            initiateIpAddress()
            settingTempServerSocket()
        } else {
            settingTempClientSocket()
        }
        val server = ServerClass(requireActivity())
        server.start()

        mContentView?.let {
            it.findViewById<View>(R.id.btn_start_client).visibility =
                View.VISIBLE
            (it.findViewById<View>(R.id.status_text) as TextView).text =
                resources
                    .getString(R.string.client_text)
            it.findViewById<View>(R.id.btn_connect).visibility = View.GONE
        }
    }

    private fun settingTempServerSocket() {
        val serverSocket = ServerSocket()
        serverSocket.reuseAddress = true
        serverSocket.bind(InetSocketAddress(8888))

        val socket = serverSocket.accept()
        clientIP = socket.inetAddress.hostAddress

        socket.close()
        serverSocket.close()
    }

    private fun settingTempClientSocket() {
        val socket = Socket()
        socket.apply {
            reuseAddress = true
            bind(null)
            connect(InetSocketAddress(IP_SERVER, 8888), 500)
            close()
        }
    }

    private fun initiateIpAddress() {
        localIP = Utils.localIPAddress()
    }

    fun showDetails(device: WifiP2pDevice) {
        this.device = device
        this.view?.visibility = View.VISIBLE
        var view = mContentView?.findViewById<View>(R.id.device_address) as TextView
        view.text = device.deviceAddress
        view = mContentView?.findViewById<View>(R.id.device_info) as TextView
        view.text = device.toString()
    }

    fun resetViews() {
        mContentView?.findViewById<View>(R.id.btn_connect)?.visibility = View.VISIBLE
        var view = mContentView?.findViewById<View>(R.id.device_address) as TextView
        view.setText(R.string.empty)
        view = mContentView?.findViewById<View>(R.id.device_info) as TextView
        view.setText(R.string.empty)
        view = mContentView?.findViewById<View>(R.id.group_owner) as TextView
        view.setText(R.string.empty)
        view = mContentView?.findViewById<View>(R.id.status_text) as TextView
        view.setText(R.string.empty)
        mContentView?.let {
            it.findViewById<View>(R.id.btn_start_client).visibility = View.GONE
        }
        view.visibility = View.GONE
    }

    class ServerClass(
        private val activity: Activity
    ) : Thread() {

        lateinit var serverSocket: ServerSocket

        override fun run() {
            try {
                serverSocket = ServerSocket()
                serverSocket.bind(InetSocketAddress(8988))
                val socket = serverSocket.accept()
                val f = File(
                    activity.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                    "file-" + System.currentTimeMillis()
                            + ".zip"
                )
                val dirs = File(f.parent)
                if (!dirs.exists()) dirs.mkdirs()
                f.createNewFile()
                Log.d(
                    MainActivity.TAG,
                    "copying files $f"
                )
                val inputstream = socket.getInputStream()
                copyFile(inputstream, FileOutputStream(f))
                Log.d(MainActivity.TAG, "file saved $f")
                activity.runOnUiThread {
                    run {
                        Toast.makeText(activity, "File copied to $f", Toast.LENGTH_SHORT).show()
                    }
                }
                serverSocket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        private const val CHOOSE_FILE_RESULT_CODE = 20
        private const val IP_SERVER: String = "192.168.49.1"
        fun copyFile(inputStream: InputStream, out: OutputStream): Boolean {
            val buf = ByteArray(1024)
            var len: Int
            try {
                while ((inputStream.read(buf).also { len = it }) != -1) {
                    out.write(buf, 0, len)
                }
                out.close()
                inputStream.close()
            } catch (e: IOException) {
                Log.d(MainActivity.TAG, e.toString())
                return false
            }
            return true
        }
    }
}