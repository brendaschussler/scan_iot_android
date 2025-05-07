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
import java.util.Timer
import java.util.TimerTask
import kotlin.collections.iterator

class PacketCapturer(private val context: Context) {

    fun capture(macList: List<String>, packetCount: Int, outputFile: String, sessionId: String, callback: (Boolean, String) -> Unit) {

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

                    // Processo secundário (para obter interface) - use outro nome de variável
                    val interfaceProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "ip -o addr show | grep $networkPrefix"))
                    val interfaceName = interfaceProcess.inputStream.bufferedReader().use { reader ->
                        Regex("""^\d+:\s+(\S+)""").find(reader.readText())?.groupValues?.get(1)
                    }
                    interfaceProcess.waitFor()


                    //val interfaceName = Regex("""^\d+:\s+(\S+)""").find(output)?.groupValues?.get(1)

                    val outputFileName = "/sdcard/${outputFile}_${sessionId}.pcap"

                    val command = "tcpdump -i $interfaceName $filter -s 0 -c $packetCount -v -w $outputFileName\n"

                    Log.d("TCPDUMP", "Executando: $command")

                    outputStream.writeBytes(command)
                    outputStream.writeBytes("exit\n")
                    outputStream.flush()

                    // Thread para ler o progresso
                    Thread {
                        try {
                            process.errorStream.bufferedReader().use { reader ->
                                val progressRegex = Regex("""Got (\d+)""")
                                val completionRegex = Regex("""(\d+) packets? captured""")

                                reader.forEachLine { line ->
                                    Log.d("TCPDUMP_STDERR", line)

                                    // Verifica progresso
                                    progressRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { count ->
                                        Log.d("COUNT", "Progresso: $count/$packetCount")
                                    }

                                    // Verifica conclusão
                                    completionRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { captured ->
                                        if (captured >= packetCount) {
                                            Log.d("COUNT", "CAPTURA CONCLUÍDA: $captured pacotes")
                                            CaptureRepository.updateCaptureState(sessionId, false)
                                            callback(true, "Captura concluída")
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("COUNT_ERROR", "Erro ao ler stderr", e)
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

    fun captureByTime(macList: List<String>, timeLimit: Long, outputFile: String, sessionId: String, callback: (Boolean, String) -> Unit) {

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
                    val timeSeconds = timeLimit / 1000
                    val outputFileName = "/sdcard/${outputFile}_${sessionId}.pcap"

                    //MINIMO É 360s
                    val command = "timeout ${timeSeconds}s tcpdump -i $interfaceName $filter -s 0 -w $outputFileName\n"

                    Log.d("TCPDUMP", "Executando: $command")

                    outputStream.writeBytes(command)
                    outputStream.writeBytes("exit\n")
                    outputStream.flush()

                    Timer().schedule(object : TimerTask() {
                        override fun run() {
                            try {
                                stopCapture(sessionId)
                            } catch (e: Exception) {
                                Log.e("TCPDUMP", "Erro ao encerrar tcpdump", e)
                            }
                        }
                    }, timeLimit)

                    for (i in 1..timeSeconds) {
                        Thread.sleep(1000)
                        val elapsedTime = i
                        Log.d("elapsedTime", "elapsedTime: $i / $timeSeconds ")
                        if (elapsedTime >= timeSeconds){
                            CaptureRepository.updateCaptureState(sessionId, false)
                            Log.d("elapsedTime", "FINALIZOU: elapsedTime: $i / $timeSeconds ")
                        }
                    }

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

    fun stopCapture(sessionId: String): Boolean {
        return try {

            val killProcess = Runtime.getRuntime().exec("su")
            val killOutputStream = DataOutputStream(killProcess.outputStream)

            killOutputStream.writeBytes("pkill -SIGINT -f \"tcpdump.*$sessionId\"\n")
            killOutputStream.writeBytes("exit\n")
            killOutputStream.flush()

            killProcess.waitFor()

            true

        } catch (e: Exception) {
            Log.e("TCPDUMP", "Error stopping tcpdump for session $sessionId", e)
            false
        }



    }
}