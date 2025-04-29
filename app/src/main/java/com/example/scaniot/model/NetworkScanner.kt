package com.example.scaniot.model

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
import android.os.Build
import java.net.InetAddress
import kotlinx.coroutines.*
import java.io.File

class NetworkScanner(private val context: Context) {

    suspend fun scanNetworkDevices(timeout: Int = 2000): List<Device> = withContext(Dispatchers.IO) {
        val devicesFound = mutableListOf<Device>()

        val localIp = getLocalIpAddress() ?: return@withContext emptyList()
        val networkPrefix = getNetworkPrefix(localIp)

        // Varre os IPs de 1 a 254
        (1..254).map { i ->
            async {
                val host = "$networkPrefix$i"
                try {
                    val address = InetAddress.getByName(host)
                    val deviceName = getReverseDnsName(host)
                        ?: "Unknown Device"
                    if (address.isReachable(timeout)) {
                        val mac = getMacFromArpCache(host) ?: "Unknown"
                        Device(
                            ip = host,
                            mac = mac,
                            name = deviceName,
                            description = "Discovered on network",
                            vendor = getVendorFromMac(mac),
                            deviceModel = "Unknown",
                            deviceLocation = "Unknown",
                            deviceVersion = "Unknown",
                            deviceType = guessDeviceType(address.hostName ?: ""),
                            userId = ""
                        )
                    } else null
                } catch (e: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull().also { devicesFound.addAll(it) }

        devicesFound
    }

    fun getReverseDnsName(ip: String): String? {
        return try {
            val host = InetAddress.getByName(ip)
            if (host.hostName != ip) host.hostName else null
        } catch (e: Exception) {
            null
        }
    }

    private fun getLocalIpAddress(): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val linkProperties = connectivityManager.getLinkProperties(network)
            linkProperties?.linkAddresses?.firstOrNull()?.address?.hostAddress
        } else {
            @Suppress("DEPRECATION")
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipAddress = wifiInfo.ipAddress
            String.format(
                "%d.%d.%d.%d",
                ipAddress and 0xff,
                ipAddress shr 8 and 0xff,
                ipAddress shr 16 and 0xff,
                ipAddress shr 24 and 0xff
            )
        }
    }

    private fun getNetworkPrefix(ipAddress: String): String {
        val parts = ipAddress.split(".")
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}." else "192.168.1."
    }

    private fun getMacFromArpCache(ip: String): String? {
        return try {
            val arpCache = File("/proc/net/arp").readLines()
            arpCache.firstOrNull { it.split(Regex("\\s+"))[0] == ip }?.split(Regex("\\s+"))?.get(3)
        } catch (e: Exception) {
            null
        }
    }

    private fun getVendorFromMac(mac: String): String {
        // Implementação básica - você pode expandir com um banco de dados OUI mais completo
        return when {
            mac.startsWith("00:1A:2B") -> "Samsung"
            mac.startsWith("00:1B:2C") -> "Xiaomi"
            mac.startsWith("00:1C:2D") -> "Apple"
            mac.startsWith("00:1D:2E") -> "Philips"
            mac.startsWith("00:1E:2F") -> "TP-Link"
            else -> "Unknown Vendor"
        }
    }

    private fun guessDeviceType(hostname: String): String {
        return when {
            hostname.contains("tv", ignoreCase = true) -> "television"
            hostname.contains("phone", ignoreCase = true) -> "smartphone"
            hostname.contains("mac", ignoreCase = true) -> "laptop"
            hostname.contains("light", ignoreCase = true) -> "light"
            hostname.contains("camera", ignoreCase = true) -> "camera"
            else -> "unknown"
        }
    }
}