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

    private val rootManager = RootManager(context)

    private val macVendorCache = mutableMapOf<String, String>()


    suspend fun getEnhancedDeviceName(ip: String, hasRootAccess: Boolean): String? {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Tenta com root primeiro
                if (hasRootAccess) {
                    getDeviceNameWithRoot(ip)?.let { return@withContext it }
                    getDeviceNameAlternative(ip)?.let { return@withContext it }
                }

                // 2. Fallback: DNS reverso
                getReverseDnsName(ip)?.let { return@withContext it }

                // 3. Fallback: NetBIOS sem root
                getNetBiosNameWithoutRoot(ip)?.let { return@withContext it }

                // 4. Último fallback
                null
            } catch (e: Exception) {
                null
            }
        }
    }

    fun lookupMacVendor(mac: String): String {
        val client = OkHttpClient()

        val request = Request.Builder()
            .url("https://api.maclookup.app/v2/macs/$mac")
            .header("User-Agent", "ScanIOTApp")  // OBRIGATÓRIO!
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                println("Erro na requisição: ${response.code}")
                return "Unknown Vendor"
            }

            val body = response.body?.string() ?: return "Unknown Vendor"
            val json = JSONObject(body)
            return json.optString("company", "Unknown Vendor")
        }
    }


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

    private fun getNetBiosNameWithoutRoot(ip: String): String? {
        return try {
            val nbtscan = ProcessBuilder()
                .command("nbtscan", "-e", ip)
                .redirectErrorStream(true)
                .start()

            nbtscan.inputStream.bufferedReader().use { reader ->
                reader.readLine()?.split("\\s+".toRegex())?.getOrNull(1)
            }
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


    /*suspend fun scanNetworkDevices(timeout: Int = 2000, hasRootAccess: Boolean = false): List<Device> = withContext(Dispatchers.IO) {
        val devicesFound = mutableListOf<Device>()

        // add first the own device that is running the app
        // because mac is not on arp cache
        getSelfDeviceInfo()?.let { devicesFound.add(it) }

        val localIp = getActiveIpAddress() ?: return@withContext emptyList()

        Log.d("LocalIp", "localIp: ${localIp}")

        val networkPrefix = getNetworkPrefix(localIp)

        Log.d("networkPrefix", "networkPrefix: ${networkPrefix}")


        if (rootManager.hasRootAccess() && !rootManager.areToolsInstalled()) {
            rootManager.installRequiredTools()
        }

        // Varre os IPs de 1 a 254
        (1..254).map { i ->
            async {
                val host = "$networkPrefix$i"
                try {
                    if (host != localIp) {  // Evita escanear o próprio IP novamente
                        val address = InetAddress.getByName(host)
                        if (address.isReachable(timeout)) {
                            val name = getDeviceName(host, rootManager.hasRootAccess()) ?: "Unknown"
                            val mac = if (hasRootAccess) {
                                getMacWithRoot(host) ?: getMacFromArpCache(host) ?: "Unknown"
                            } else {
                                getMacFromArpCache(host) ?: "Unknown"
                            }

                            Device(
                                ip = host,
                                mac = mac,
                                name = name,
                                description = "Discovered on network",
                                vendor = getVendorFromMac(mac),
                                deviceModel = "Unknown",
                                deviceLocation = "Unknown",
                                deviceVersion = "Unknown",
                                deviceType = guessDeviceType(address.hostName ?: ""),
                                userId = ""
                            )
                        } else null
                    } else null
                } catch (e: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull().also { devicesFound.addAll(it) }

        devicesFound
    }*/

    /*fun getSelfDeviceInfo(): Device? {
        return try {
            // Obtém o IPv4 do WiFi
            val ipv4 = getWifiIpAddress()

            // Obtém o MAC address
            val mac = getSelfMacAddress()

            if (ipv4 != null && mac != null) {
                Device(
                    ip = ipv4,
                    mac = mac,
                    name = Build.MODEL,
                    description = "Este dispositivo",
                    vendor = getVendorFromMac(mac),
                    deviceModel = Build.MODEL,
                    deviceLocation = "Local",
                    deviceVersion = Build.VERSION.RELEASE,
                    deviceType = "smartphone",
                    userId = ""
                )

            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
*/
    private fun getWifiIpAddress(): String? {
        return try {
            @Suppress("DEPRECATION")
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ip = wifiInfo.ipAddress

            if (ip == 0) return null

            String.format("%d.%d.%d.%d",
                ip and 0xff,
                ip shr 8 and 0xff,
                ip shr 16 and 0xff,
                ip shr 24 and 0xff
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun getMacWithRoot(ip: String): String? {
        return try {
            val command = "ip neigh show $ip | awk '{print \$5}'"
            val result = Runtime.getRuntime()
                .exec(arrayOf("su", "-c", command))
                .inputStream.bufferedReader()
                .readText()
                .trim()

            if (result.isNotEmpty() && result != "00:00:00:00:00:00") result else null
        } catch (e: Exception) {
            null
        }
    }

    fun getSelfMacAddress(): String? {
        return try {
            // Tenta primeiro sem root
            getSelfMacWithoutRoot() ?: getSelfMacWithRoot()
        } catch (e: Exception) {
            null
        }
    }

    private fun getSelfMacWithoutRoot(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()

                // Ignora interfaces locais e loopback
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val mac = networkInterface.hardwareAddress ?: continue

                // Formata o MAC address
                val sb = StringBuilder()
                for (b in mac) {
                    sb.append(String.format("%02X:", b))
                }
                if (sb.isNotEmpty()) {
                    sb.deleteCharAt(sb.length - 1)
                }

                // Prefere endereços não locais
                if (!isLocalMac(sb.toString())) {
                    return sb.toString()
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun isLocalMac(mac: String): Boolean {
        // MACs locais começam com estes prefixos
        val localPrefixes = listOf("02:", "06:", "0A:", "0E:")
        return localPrefixes.any { mac.startsWith(it) }
    }

    private fun getSelfMacWithRoot(): String? {
        return try {
            // Comando para obter MAC da interface WiFi (wlan0)
            val command = "cat /sys/class/net/wlan0/address"

            Runtime.getRuntime().exec(arrayOf("su", "-c", command)).let { process ->
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val mac = reader.readLine()?.trim()
                process.waitFor()
                if (process.exitValue() == 0) mac else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getReverseDnsName(ip: String): String? {
        return try {
            val host = InetAddress.getByName(ip)
            if (host.hostName != ip) host.hostName else null
        } catch (e: Exception) {
            null
        }
    }

    fun getDeviceNameWithRoot(ip: String): String? {
        return try {
            val command = "nmblookup -A $ip | grep '<00>' | grep -v GROUP | awk '{print \$1}'"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val name = reader.readLine()?.trim()
            process.waitFor()
            if (process.exitValue() == 0 && !name.isNullOrEmpty()) name else null
        } catch (e: Exception) {
            null
        }
    }

    fun getDeviceNameAlternative(ip: String): String? {
        return try {
            val command = "smbutil lookup $ip | grep 'NetBIOS' | awk '{print \$3}'"
            Runtime.getRuntime().exec(arrayOf("su", "-c", command)).let { process ->
                val name = process.inputStream.bufferedReader().use { it.readLine()?.trim() }
                process.waitFor()
                if (process.exitValue() == 0) name else null
            }
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

    private fun getMacFromArpCache(ip: String): String? {
        return try {
            // First try reading ARP cache directly
            val arpFile = File("/proc/net/arp")
            if (arpFile.canRead()) {
                val arpCache = arpFile.readLines()
                val entry = arpCache.firstOrNull { it.split(Regex("\\s+"))[0] == ip }
                entry?.split(Regex("\\s+"))?.get(3)?.takeIf { it != "00:00:00:00:00:00" }
            } else {
                // If we can't read directly, try with su
                val process = Runtime.getRuntime().exec("su -c cat /proc/net/arp")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val arpCache = reader.readLines()
                arpCache.firstOrNull { it.split(Regex("\\s+"))[0] == ip }?.split(Regex("\\s+"))?.get(3)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getMacFromIp(ip: String): String? {
        return try {
            // Tenta ler via ARP cache (requer root)
            val process = Runtime.getRuntime().exec("ip neigh show $ip")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            reader.close()

            // Formato esperado: "192.168.1.1 dev wlan0 lladdr aa:bb:cc:dd:ee:ff"
            line?.split(" ")?.getOrNull(4)?.takeIf { it.matches(Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getVendorFromMac(mac: String): String {

        val vendor = MacVendorResolver.getVendor(mac)
        Log.d("MAC", "getVendorFromMac: $vendor")

        return vendor
    }


    /*private fun getVendorFromMac(mac: String): String {
        // Verifica o cache primeiro
        macVendorCache[mac.uppercase()]?.let {
            return it
        }

        // Tenta consultar macvendors.com
        try {
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url("https://api.macvendors.com/$mac")
                .build()

            val response = client.newCall(request).execute()
            Log.d("VendorLookup", "Request URL: ${request.url}")
            Log.d("VendorLookup", "Response code: ${response.code}")
            if (response.isSuccessful) {
                val vendor = response.body?.string()?.trim().orEmpty()
                Log.d("VendorLookup", "Vendor response: $vendor")
                macVendorCache[mac.uppercase()] = vendor // Armazena no cache
                return vendor
            }
        } catch (e: Exception) {
            Log.e("MacVendorLookup", "Erro ao consultar macvendors.com", e)
        }

        return "Unknown Vendor"
    }*/


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