package com.example.scaniot.model

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import java.net.InetAddress
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.NetworkInterface
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject


class NetworkScanner(private val context: Context) {


    private fun getActiveIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue

                val addresses = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is InetAddress && addr.hostAddress.indexOf(':') < 0) {
                        return addr.hostAddress // IPv4 válido
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NetworkScanner", "Erro ao obter IP ativo", e)
        }
        return null
    }

    private fun getNetworkPrefix(ipAddress: String): String {
        val parts = ipAddress.split(".")
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}." else "192.168.43." // hotspot padrão
    }

    suspend fun getConnectedHotspotDevices(): List<Device> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<Device>()
        val localIp = getActiveIpAddress() ?: return@withContext devices
        val networkPrefix = getNetworkPrefix(localIp)

        val suProcess = Runtime.getRuntime().exec("su")
        val outputStream = DataOutputStream(suProcess.outputStream)
        val inputStream = suProcess.inputStream

        // Envia o comando para obter dispositivos da subrede via ip neigh
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