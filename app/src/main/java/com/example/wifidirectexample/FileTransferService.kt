package com.example.wifidirectexample

import android.app.IntentService
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class FileTransferService : IntentService {
    constructor(name: String?) : super(name) {}
    constructor() : super("FileTransferService") {}

    override fun onHandleIntent(intent: Intent?) {
        val context = applicationContext
        if (intent?.action == ACTION_SEND_FILE) {
            val fileUri = intent.extras?.getString(EXTRAS_FILE_PATH)
            val host = intent.extras?.getString(EXTRAS_GROUP_OWNER_ADDRESS)
            val port = intent.extras?.getInt(EXTRAS_GROUP_OWNER_PORT)
            val isHost = intent.extras?.getBoolean(EXTRAS_IS_GROUP_OWNER)
            val socket = Socket()
            try {
                if (isHost == true) {
                    val serverSocket = ServerSocket(port ?: 0)
                    serverSocket.reuseAddress = true
                    val clientSocket = serverSocket.accept()

                    val inputstream = clientSocket.getInputStream()

                    val f = File(
                        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                        "file-" + System.currentTimeMillis()
                                + ".zip"
                    )
                    val dirs = File(f.parent)
                    if (!dirs.exists()) dirs.mkdirs()
                    f.createNewFile()
                    Log.d(
                        MainActivity.TAG,
                        "server_service: copying files $f"
                    )

                    DeviceDetailFragment.copyFile(inputstream, FileOutputStream(f))
                    Log.d(MainActivity.TAG, "file saved $f")
                    serverSocket.close()
                } else {
                    Log.d(MainActivity.TAG, "Opening client socket - ")
                    socket.bind(null)
                    socket.connect(InetSocketAddress(host, port ?: 0), SOCKET_TIMEOUT)
                    Log.d(MainActivity.TAG, "Client socket - " + socket.isConnected)
                    val stream = socket.getOutputStream()
                    val cr = context.contentResolver
                    var inputStream: InputStream? = null
                    try {
                        inputStream = cr.openInputStream(Uri.parse(fileUri))
                    } catch (e: FileNotFoundException) {
                        Log.d(MainActivity.TAG, e.toString())
                    }
                    inputStream?.let {
                        DeviceDetailFragment.copyFile(it, stream)
                    }
                    Log.d(MainActivity.TAG, "Client: Data written")
                }
            } catch (e: IOException) {
                Log.e(MainActivity.TAG, e.message.toString())
            } finally {
                if (socket.isConnected) {
                    try {
                        socket.close()
                    } catch (e: IOException) {
                        Log.e(MainActivity.TAG, e.message.toString())
                    }
                }
            }
        }
    }

    companion object {
        private const val SOCKET_TIMEOUT = 5000
        const val ACTION_SEND_FILE = "com.example.android.wifidirect.SEND_FILE"
        const val EXTRAS_FILE_PATH = "file_url"
        const val EXTRAS_GROUP_OWNER_ADDRESS = "go_host"
        const val EXTRAS_GROUP_OWNER_PORT = "go_port"
        const val EXTRAS_IS_GROUP_OWNER = "go_is_group_owner"
    }
}