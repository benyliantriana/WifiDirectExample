package com.example.wifidirectexample

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.*

object Utils {
    fun getConnectedDevices(YourPhoneIPAddress: String?): String? {
        var clientIP = ""
        val myIPArray = YourPhoneIPAddress?.split(".")?.toTypedArray()
        var currentPingAddr: InetAddress
        if (myIPArray == null) return null
        for (i in (0..255)) {
            val tempIP = myIPArray[0] + "." +
                    myIPArray[1] + "." +
                    myIPArray[2] + "." +
                    i.toString()

            currentPingAddr = InetAddress.getByName(
                tempIP
            )

            if (i.toString() != myIPArray[3]) {
                if (currentPingAddr.isReachable(50)) {
                    clientIP = tempIP
                }
            }
            if (clientIP != "") break
        }
        return clientIP
    }

    fun localIPAddress(): String? {
        try {
            val en: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf: NetworkInterface = en.nextElement()
                val enumIpAddr: Enumeration<InetAddress> = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress: InetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.getHostAddress()
                    }
                }
            }
        } catch (ex: SocketException) {
            ex.printStackTrace()
        }
        return null
    }
}