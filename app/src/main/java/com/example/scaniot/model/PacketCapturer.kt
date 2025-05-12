package com.example.scaniot.model

import android.content.Context
import android.content.Intent
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

    companion object {
        const val PROGRESS_UPDATE_ACTION = "com.example.scaniot.PROGRESS_UPDATE"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_TOTAL = "total"
    }

    private fun sendDeviceProgressUpdate(sessionId: String, mac: String, progress: Int, total: Int) {
        val intent = Intent(PROGRESS_UPDATE_ACTION).apply {
            putExtra(EXTRA_SESSION_ID, sessionId) // Combine sessionId and mac
            putExtra(EXTRA_PROGRESS, progress)
            putExtra(EXTRA_TOTAL, total)
            putExtra("mac", mac) // Add MAC as separate extra
        }
        context.sendBroadcast(intent)
    }


    fun capture(macList: List<String>, packetCount: Int, outputFile: String, sessionId: String, callback: (Boolean, String) -> Unit) {
        macList.forEach { mac ->
            Thread {
                try {
                    val process = Runtime.getRuntime().exec("su")
                    val outputStream = DataOutputStream(process.outputStream)
                    val localIp = getActiveIpAddress()

                    if (localIp != null) {
                        val networkPrefix = getNetworkPrefix(localIp)
                        val interfaceProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "ip -o addr show | grep $networkPrefix"))
                        val interfaceName = interfaceProcess.inputStream.bufferedReader().use { reader ->
                            Regex("""^\d+:\s+(\S+)""").find(reader.readText())?.groupValues?.get(1)
                        }
                        interfaceProcess.waitFor()

                        val outputFileName = "/sdcard/${outputFile}_${mac}_${sessionId}.pcap"
                        val command = "tcpdump -i $interfaceName ether host $mac -s 0 -c $packetCount -v -w $outputFileName\n"
                        Log.d("TCPDUMP", "Executando: $command")

                        outputStream.writeBytes(command)
                        outputStream.writeBytes("exit\n")
                        outputStream.flush()

                        // Progress tracking thread for this MAC
                        Thread {
                            try {
                                process.errorStream.bufferedReader().use { reader ->
                                    val progressRegex = Regex("""Got (\d+)""")
                                    val completionRegex = Regex("""(\d+) packets? captured""")

                                    reader.forEachLine { line ->
                                        progressRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { count ->
                                            Log.d("TCPDUMP", "$count / $packetCount")
                                            CaptureRepository.updateDeviceCaptureProgress(sessionId, mac, count, packetCount)
                                            sendDeviceProgressUpdate(sessionId, mac, count, packetCount)
                                        }

                                        completionRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { captured ->
                                            if (captured >= packetCount) {
                                                Log.d("TCPDUMP", "$captured / $packetCount")
                                                CaptureRepository.updateDeviceCaptureProgress(sessionId, mac, packetCount, packetCount)
                                                CaptureRepository.updateDeviceCaptureState(sessionId, mac, false)
                                                sendDeviceProgressUpdate(sessionId, mac, captured, packetCount)
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("COUNT_ERROR", "Error reading stderr for MAC $mac", e)
                            }
                        }.start()

                        process.waitFor()
                    }
                } catch (e: Exception) {
                    Log.e("TCPDUMP", "Error capturing for MAC $mac", e)
                }
            }.start()
        }
        callback(true, "Capture started for all devices")
    }

    fun captureByTime(macList: List<String>, timeLimit: Long, outputFile: String, sessionId: String, callback: (Boolean, String) -> Unit) {
        macList.forEach { mac ->

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
                        val outputFileName = "/sdcard/${outputFile}_${mac}_${sessionId}.pcap"

                        val command = "timeout ${timeSeconds}s tcpdump -i $interfaceName ether host $mac -s 0 -v -w $outputFileName\n"

                        Log.d("TCPDUMP", "Executando: $command")

                        outputStream.writeBytes(command)
                        outputStream.writeBytes("exit\n")
                        outputStream.flush()

                        for (i in 1..timeSeconds) {
                            Thread.sleep(1000)
                            val elapsedTime = i
                            Log.d("elapsedTime", "elapsedTime: $elapsedTime / $timeSeconds ")
                            CaptureRepository.updateDeviceCaptureProgress(sessionId, mac, elapsedTime.toInt(), timeSeconds.toInt())
                            sendDeviceProgressUpdate(sessionId, mac, elapsedTime.toInt(), timeSeconds.toInt())

                            if (elapsedTime >= timeSeconds){
                                CaptureRepository.updateCaptureState(sessionId, false)
                                Log.d("elapsedTime", "FINALIZOU: elapsedTime: $i / $timeSeconds ")
                                CaptureRepository.updateDeviceCaptureProgress(sessionId, mac, elapsedTime.toInt(), elapsedTime.toInt())
                                CaptureRepository.updateDeviceCaptureState(sessionId, mac, false)
                                sendDeviceProgressUpdate(sessionId, mac, elapsedTime.toInt(), elapsedTime.toInt())
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

    fun stopDeviceCapture(sessionId: String, mac: String): Boolean {
        return try {

            val killProcess = Runtime.getRuntime().exec("su")
            val killOutputStream = DataOutputStream(killProcess.outputStream)

            //killOutputStream.writeBytes("pkill -SIGINT -f \"tcpdump.*$sessionId\"\n")
            killOutputStream.writeBytes("pkill -SIGINT -f \"tcpdump.*${mac}_${sessionId}\"\n")
            killOutputStream.writeBytes("exit\n")
            killOutputStream.flush()

            killProcess.waitFor()

            true

        } catch (e: Exception) {
            Log.e("TCPDUMP", "Error stopping tcpdump for device $mac in session $sessionId", e)
            false
        }

    }
}