package com.example.scaniot.model

import android.content.Context
import android.util.Log
import java.net.InetAddress
import kotlinx.coroutines.*
import java.io.DataOutputStream
import java.net.NetworkInterface

class NetworkScanner(private val context: Context) {


    fun getActiveIpAddress(): String? {
        val hotspotKeywords = listOf("ap", "softap", "wlan1", "wlan2", "swlan")
        var fallbackIp: String? = null

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val name = intf.name ?: continue
                if (!intf.isUp || intf.isLoopback) continue

                val addresses = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is InetAddress && addr.hostAddress.indexOf(':') < 0) {
                        val ip = addr.hostAddress

                        // Try hotspot interfaces
                        if (hotspotKeywords.any { keyword -> name.contains(keyword) }) {
                            return ip
                        }

                        // Else, any active
                        if (fallbackIp == null) {
                            fallbackIp = ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NetworkScanner", "Error getting IP", e)
        }

        return fallbackIp
    }

    private fun getNetworkPrefix(ipAddress: String): String {
        val parts = ipAddress.split(".")
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}." else "192.168.43."
    }

    suspend fun getConnectedHotspotDevices(): List<Device> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<Device>()
        val localIp = getActiveIpAddress() ?: return@withContext devices
        val networkPrefix = getNetworkPrefix(localIp)

        val suProcess = Runtime.getRuntime().exec("su")
        val outputStream = DataOutputStream(suProcess.outputStream)
        val inputStream = suProcess.inputStream

        val command = "ip neigh | grep '^$networkPrefix'\n"
        outputStream.writeBytes(command)
        outputStream.writeBytes("exit\n")
        outputStream.flush()

        val lines = inputStream.bufferedReader().readLines()

        for (line in lines) {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 5) {
                val ip = parts[0]
                val mac = parts[4]
                if (mac.matches(Regex("([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}")) && mac != "00:00:00:00:00:00") {
                    devices.add(
                        Device(
                            ip = ip,
                            mac = mac,
                            name = "Unknown",
                            description = "Discovered on network",
                            vendor = getVendorFromMac(mac),
                            deviceModel = "Unknown",
                            deviceLocation = "Unknown",
                            deviceVersion = "Unknown",
                            deviceType = "Unknown",
                            userId = ""
                        )
                    )
                }
            }
        }

        suProcess.waitFor()
        devices
    }

    suspend fun getVendorFromMac(mac: String): String {

        val vendor = MacVendorResolver.getVendor(mac)
        Log.d("MAC", "getVendorFromMac: $vendor")

        return vendor
    }
}