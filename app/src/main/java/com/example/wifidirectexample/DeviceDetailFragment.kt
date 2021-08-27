package com.example.wifidirectexample

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket

class DeviceDetailFragment() : Fragment(), ConnectionInfoListener {
    private var mContentView: View? = null
    private var device: WifiP2pDevice? = null
    private var info: WifiP2pInfo? = null
    var progressDialog: ProgressDialog? = null

    @SuppressLint("InflateParams")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        mContentView = inflater.inflate(R.layout.device_detail, null)
        mContentView?.findViewById<View>(R.id.btn_connect)?.setOnClickListener {
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
        mContentView?.findViewById<View>(R.id.btn_disconnect)
            ?.setOnClickListener { (activity as DeviceListFragment.DeviceActionListener).disconnect() }
        mContentView?.findViewById<View>(R.id.btn_start_client)
            ?.setOnClickListener { // Allow user to pick an image from Gallery or other
                // registered apps
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "image/*"
                startActivityForResult(
                    intent,
                    CHOOSE_FILE_RESULT_CODE
                )
            }
        return mContentView
    }

    @SuppressLint("SetTextI18n")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri = data?.data
        val statusText = mContentView?.findViewById<View>(R.id.status_text) as TextView
        statusText.text = "Sending: $uri"
        Log.d(
            MainActivity.TAG,
            "Intent----------- $uri"
        )
        val serviceIntent = Intent(requireContext(), FileTransferService::class.java)
        serviceIntent.action = FileTransferService.ACTION_SEND_FILE
        serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, uri.toString())
        serviceIntent.putExtra(
            FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
            info?.groupOwnerAddress?.hostAddress
        )
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8988)
        requireActivity().startService(serviceIntent)
        Log.d(
            MainActivity.TAG,
            "send ??   $uri"
        )
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

        if (info.groupFormed && info.isGroupOwner) {
            mContentView?.let {
                FileServerAsyncTask(requireContext(), it.findViewById(R.id.status_text))
                    .execute()
            }
        } else if (info.groupFormed) {
            mContentView?.let {
                it.findViewById<View>(R.id.btn_start_client).visibility =
                    View.VISIBLE
                (it.findViewById<View>(R.id.status_text) as TextView).text =
                    resources
                        .getString(R.string.client_text)
            }
        }
        mContentView?.let {
            it.findViewById<View>(R.id.btn_connect).visibility = View.GONE
        }
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

    @SuppressLint("StaticFieldLeak")
    class FileServerAsyncTask(private val context: Context, statusText: View) :
        AsyncTask<Void?, Void?, String?>() {
        @SuppressLint("StaticFieldLeak")
        private val statusText: TextView = statusText as TextView
        override fun doInBackground(vararg params: Void?): String? {
            try {
                val serverSocket = ServerSocket(8988)
                Log.d(
                    MainActivity.TAG,
                    "Server: Socket opened"
                )
                val client = serverSocket.accept()
                Log.d(
                    MainActivity.TAG,
                    "Server: connection done"
                )
                val f = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                    "wifip2pshared-" + System.currentTimeMillis()
                            + ".jpg"
                )
                val dirs = File(f.parent)
                if (!dirs.exists()) dirs.mkdirs()
                f.createNewFile()
                Log.d(
                    MainActivity.TAG,
                    "server: copying files $f"
                )
                val inputstream = client.getInputStream()
                copyFile(inputstream, FileOutputStream(f))
                serverSocket.close()
                return f.absolutePath
            } catch (e: IOException) {
                Log.e(
                    MainActivity.TAG,
                    e.message.toString()
                )
                return null
            }
        }

        override fun onPostExecute(result: String?) {
            if (result != null) {
                statusText.text = "File copied - $result"
                val recvFile = File(result)
                val fileUri = FileProvider.getUriForFile(
                    context,
                    "com.example.wifidirectexample.fileprovider",
                    recvFile
                )
                val intent = Intent()
                intent.action = Intent.ACTION_VIEW
                intent.setDataAndType(fileUri, "image/*")
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.startActivity(intent)
            }
        }

        override fun onPreExecute() {
            statusText.text = "Opening a server socket"
        }

    }

    companion object {
        private const val CHOOSE_FILE_RESULT_CODE = 20
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