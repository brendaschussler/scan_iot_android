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

suspend fun getVendorFromMacOnline(mac: String): String = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.macvendors.com/${mac}")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                response.body?.string()?.trim() ?: "Unknown"
            } else {
                "Unknown"
            }
        }
    } catch (e: Exception) {
        "Unknown"
    }
}


class NetworkScanner(private val context: Context) {

    private suspend fun getDeviceName(ip: String, hasRootAccess: Boolean): String? = withContext(Dispatchers.IO) {
        // Tentativa 1: nmblookup (mais confiável para Windows/Mac)
        if (hasRootAccess) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "nmblookup -A $ip"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    if (line?.contains("<00>") == true && !line.contains("GROUP")) {
                        return@withContext line?.trim()?.split("\\s+".toRegex())?.getOrNull(0)
                    }
                }
            } catch (e: Exception) {
                Log.e("NetworkScanner", "nmblookup falhou", e)
            }
        }

        // Tentativa 2: nbtscan (alternativa)
        if (hasRootAccess) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "nbtscan -v $ip"))
                val output = process.inputStream.bufferedReader().use { it.readText() }
                val match = Regex("([A-Za-z0-9_-]+)\\s+<00>").find(output)
                return@withContext match?.groupValues?.get(1)
            } catch (e: Exception) {
                Log.e("NetworkScanner", "nbtscan falhou", e)
            }
        }

        // Tentativa 3: DNS reverso (funciona para alguns dispositivos)
        try {
            val hostName = InetAddress.getByName(ip).hostName
            if (hostName != ip) return@withContext hostName
        } catch (e: Exception) {
            Log.e("NetworkScanner", "DNS reverso falhou", e)
        }

        // Tentativa 4: NetBIOS sem root
        try {
            val process = Runtime.getRuntime().exec("nbtscan $ip")
            val output = process.inputStream.bufferedReader().use { it.readText() }
            return@withContext output.split("\\s+".toRegex())[1]
        } catch (e: Exception) {
            null
        }
    }

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
                    val name = getDeviceName(ip, true) ?: "Unknown"
                    devices.add(
                        Device(
                            ip = ip,
                            mac = mac,
                            name = name,
                            description = "Discovered on network",
                            vendor = getVendorFromMac(mac),
                            deviceModel = "Unknown",
                            deviceLocation = "Unknown",
                            deviceVersion = "Unknown",
                            deviceType = guessDeviceType(name),
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