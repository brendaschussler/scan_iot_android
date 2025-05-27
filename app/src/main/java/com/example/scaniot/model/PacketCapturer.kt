package com.example.scaniot.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.DataOutputStream
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.iterator
import kotlin.math.max
import kotlin.math.min

class PacketCapturer(private val context: Context) {


    companion object {
        const val PROGRESS_UPDATE_ACTION = "com.example.scaniot.PROGRESS_UPDATE"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_TOTAL = "total"
        const val EXTRA_END = "end"
        const val EXTRA_FILENAME = "filename"
        val timeCaptureThreads: ConcurrentHashMap<String, Thread> = ConcurrentHashMap()
    }


    private fun sendDeviceProgressUpdate(sessionId: String, mac: String, progress: Int, total: Int, end: Long, filename: String) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val intent = Intent(PROGRESS_UPDATE_ACTION).apply {
            putExtra(EXTRA_SESSION_ID, sessionId) // Combine sessionId and mac
            putExtra(EXTRA_PROGRESS, progress)
            putExtra(EXTRA_TOTAL, total)
            putExtra(EXTRA_END, end)
            putExtra(EXTRA_FILENAME, filename)
            putExtra("mac", mac) // Add MAC as separate extra
        }
        context.sendBroadcast(intent)
    }


    fun capture(macList: List<String>, packetCount: Int, outputFile: String, sessionId: String, callback: (Boolean, String) -> Unit) {
        val UPDATE_INTERVAL = max(1, packetCount / 20) // 5% = 1/20
        // Calcula os marcos de 5% em 5%
        val milestones = (1..20).map { it * packetCount / 20 }.distinct()


        macList.forEach { mac ->
            var nextMilestoneIndex = 0


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

                                            // Verifica se atingiu ou ultrapassou o marco atual
                                            if (nextMilestoneIndex < milestones.size && count >= milestones[nextMilestoneIndex]) {
                                                while (nextMilestoneIndex < milestones.size && count >= milestones[nextMilestoneIndex]) {
                                                    nextMilestoneIndex++
                                                }
                                                val progressCount = min(count, packetCount)
                                                Log.d("TCPDUMP", "DENTRO DO PORCENTO: $count / $packetCount")
                                                CaptureRepository.updateDeviceCaptureProgress(sessionId, mac, progressCount, packetCount, System.currentTimeMillis(), outputFile)
                                            }

                                            /*if (count % UPDATE_INTERVAL == 0 || count == packetCount) {
                                                Log.d("TCPDUMP", "DENTRO DO PORCENTO: $count / $packetCount")
                                                CaptureRepository.updateDeviceCaptureProgress(sessionId, mac, count, packetCount, System.currentTimeMillis(), outputFile )
                                            }*/
                                            sendDeviceProgressUpdate(sessionId, mac, count, packetCount, System.currentTimeMillis(), outputFile)
                                        }

                                        completionRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { captured ->
                                            if (captured >= packetCount) {
                                                Log.d("TCPDUMP", "$captured / $packetCount")
                                                CaptureRepository.updateDeviceCaptureProgress(sessionId, mac, packetCount, packetCount, System.currentTimeMillis(), outputFile)
                                                CaptureRepository.updateDeviceCaptureState(sessionId, mac, false)
                                                sendDeviceProgressUpdate(sessionId, mac, captured, packetCount, System.currentTimeMillis(), outputFile)
                                                uploadPcapToFirebase(context, outputFileName, outputFile, sessionId, mac)
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

    //val timeCaptureThreads: ConcurrentHashMap<String, Thread> = ConcurrentHashMap()


    fun captureByTime(macList: List<String>, timeLimit: Long, outputFile: String, sessionId: String, callback: (Boolean, String) -> Unit) {

        val totalSeconds = timeLimit / 1000
        val UPDATE_INTERVAL = max(1, totalSeconds / 20)

        macList.forEach { mac ->

            val key = "${sessionId}_${mac.lowercase()}"

            Log.d("CAPTURE", "Salvando thread com key: $key")

            val captureThread = Thread {

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
                        val ifaceProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", cmdIp))
                        val reader = ifaceProcess.inputStream.bufferedReader()
                        val output = reader.readText()
                        ifaceProcess.waitFor()

                        val interfaceName = Regex("""^\d+:\s+(\S+)""").find(output)?.groupValues?.get(1)
                        val timeSeconds = timeLimit / 1000
                        val outputFileName = "/sdcard/${outputFile}_${mac}_${sessionId}.pcap"

                        val command = "timeout ${timeSeconds}s tcpdump -i $interfaceName ether host $mac -s 0 -v -w $outputFileName\n"

                        Log.d("TCPDUMP", "Executando: $command")

                        outputStream.writeBytes(command)
                        outputStream.writeBytes("exit\n")
                        outputStream.flush()

                        //timeCaptureRunning["${sessionId}_$mac"] = AtomicBoolean(true)


                        for (i in 1..timeSeconds) {

                            Thread.sleep(1000)
                            val elapsedTime = i

                            if ((i % UPDATE_INTERVAL).toInt() == 0){
                                CaptureRepository.updateDeviceCaptureProgress(sessionId, mac, elapsedTime.toInt(), timeSeconds.toInt(), System.currentTimeMillis(), outputFile)
                                Log.d("elapsedTime", "elapsedTime DENTRO DO %: $elapsedTime / $timeSeconds ")
                            }

                            Log.d("elapsedTime", "elapsedTime: $elapsedTime / $timeSeconds ")

                            sendDeviceProgressUpdate(sessionId, mac, elapsedTime.toInt(), timeSeconds.toInt(), System.currentTimeMillis(), outputFile)

                            if (elapsedTime >= timeSeconds){
                                CaptureRepository.updateCaptureState(sessionId, false)
                                Log.d("elapsedTime", "FINALIZOU: elapsedTime: $i / $timeSeconds ")
                                CaptureRepository.updateDeviceCaptureProgress(sessionId, mac, elapsedTime.toInt(), elapsedTime.toInt(), System.currentTimeMillis(), outputFile)
                                CaptureRepository.updateDeviceCaptureState(sessionId, mac, false)
                                sendDeviceProgressUpdate(sessionId, mac, elapsedTime.toInt(), elapsedTime.toInt(), System.currentTimeMillis(), outputFile)
                                uploadPcapToFirebase(context, outputFileName, outputFile, sessionId, mac)
                            }
                        }

                        val exitCode = process.waitFor()
                        callback(exitCode == 0, outputFile)

                    } else {
                        callback(false, "IP não encontrado")
                    }
                } catch (e: InterruptedException) {
                    Log.d("CAPTURE", "Thread interrompida para $key")
                } finally {
                    timeCaptureThreads.remove(key)
                    //timeCaptureRunning.remove(key)
                    Log.d("CAPTURE", "Cleanup finalizado para $key")
                }
            }
            timeCaptureThreads[key] = captureThread
            Log.d("CAPTURE", "captureThread: $captureThread")
            Log.d("CAPTURE", "timeCaptureThreads[key]: $timeCaptureThreads[key]")
            captureThread.start()

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

    fun stopDeviceCapture(device: Device): Boolean {
        return try {

            val outputFileName = "/sdcard/${device.filename}_${device.mac}_${device.sessionId}.pcap"

            uploadPcapToFirebase(context, outputFileName, device.filename, device.sessionId, device.mac)

            val key = "${device.sessionId}_${device.mac.lowercase()}"

            Log.d("STOPPED", "Tentando parar com key: $key")

            val thread = timeCaptureThreads[key]

            val killProcess = Runtime.getRuntime().exec("su")
            val killOutputStream = DataOutputStream(killProcess.outputStream)

            //killOutputStream.writeBytes("pkill -SIGINT -f \"tcpdump.*$sessionId\"\n")
            killOutputStream.writeBytes("pkill -SIGINT -f \"tcpdump.*${device.mac}_${device.sessionId}\"\n")
            killOutputStream.writeBytes("exit\n")
            killOutputStream.flush()

            killProcess.waitFor()

            if (thread != null) {
                thread.interrupt()
                Log.d("STOPPED", "Thread interrompida: $thread")
            } else {
                Log.d("STOPPED", "⚠Thread não encontrada: $thread")
            }

            true

        } catch (e: Exception) {
            Log.e("TCPDUMP", "Error stopping tcpdump for device ${device.mac} in session ${device.sessionId}", e)
            false
        }

    }

    // Adicione esta função na classe PacketCapturer
    private fun uploadPcapToFirebase(context: Context, filePath: String, outputFile: String, sessionId: String, mac: String) {
        val storage = FirebaseStorage.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val file = File(filePath)
        if (!file.exists()) {
            Log.e("UPLOAD", "PCAP file not found: $filePath")
            return
        }

        // Crie uma referência única para o arquivo no Storage
        val fileName = "pcaps/${userId}/${outputFile}_${mac}_${sessionId}.pcap"
        val storageRef = storage.reference.child(fileName)

        // Upload do arquivo (sem listener de progresso)
        storageRef.putFile(Uri.fromFile(file))
            .addOnSuccessListener {
                // Get the download URL
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    val downloadUrl = uri.toString()
                    Log.d("UPLOAD", "Upload successful. URL: $downloadUrl")

                    // Atualizar o Firestore com a URL do arquivo (silenciosamente)
                    updatePcapUrlInFirestore(sessionId, mac, downloadUrl)
                }
            }
            .addOnFailureListener { exception ->
                Log.e("UPLOAD", "Upload failed", exception)
            }
    }

    private fun updatePcapUrlInFirestore(sessionId: String, mac: String, downloadUrl: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance().collection("captured_list")
            .document(userId)
            .collection("captures")
            .document(sessionId)
            .update(
                "devices.$mac.pcapUrl", downloadUrl,
                "lastUpdated", FieldValue.serverTimestamp()
            )
            .addOnFailureListener { e ->
                Log.e("FIRESTORE", "Error updating pcap URL", e)
            }
    }

}