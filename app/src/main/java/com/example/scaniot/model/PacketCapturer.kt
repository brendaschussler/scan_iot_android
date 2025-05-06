package com.example.scaniot.model

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import kotlin.collections.iterator

class PacketCapturer(private val context: Context) {

    fun capture(macList: List<String>, packetCount: Int, outputFile: String, callback: (Boolean, String) -> Unit) {

        Thread {
            try {
                val process = Runtime.getRuntime().exec("su")
                val outputStream = DataOutputStream(process.outputStream)

                val filter = macList.joinToString(" or ") { "ether host $it" }
                val localIp = getActiveIpAddress()
                Log.d("TCPDUMP", "LocalIp: $localIp")

                if (localIp != null) {
                    val networkPrefix = getNetworkPrefix(localIp)
                    Log.d("TCPDUMP", "NetworkPrefix: $networkPrefix")
                    val cmdIp = "ip -o addr show | grep $networkPrefix"
                    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmdIp))
                    val reader = process.inputStream.bufferedReader()
                    val output = reader.readText()
                    process.waitFor()

                    val interfaceName = Regex("""^\d+:\s+(\S+)""").find(output)?.groupValues?.get(1)

                    val command = "tcpdump -i $interfaceName $filter -s 0 -c $packetCount -v -w $outputFile\n"

                    Log.d("TCPDUMP", "Executando: $command")

                    outputStream.writeBytes(command)
                    outputStream.writeBytes("exit\n")
                    outputStream.flush()

                    // Thread para ler o progresso
                    Thread {
                        val errorReader = process.errorStream.bufferedReader()
                        var line: String?
                        val regex = Regex("""Got (\d+)""")

                        while (errorReader.readLine().also { line = it } != null) {
                            line?.let { logLine ->
                                regex.find(logLine)?.groupValues?.get(1)?.toIntOrNull()?.let { count ->
                                    Log.d("COUNT", "packets: $count")
                                }
                            }
                        }
                    }.start()

                    val exitCode = process.waitFor()
                    callback(exitCode == 0, outputFile)
                } else {
                    callback(false, "IP não encontrado")
                }
            } catch (e: Exception) {
                callback(false, "Erro: ${e.message}")
            }
        }.start()
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
}