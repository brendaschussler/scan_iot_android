package com.example.scaniot.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.DataOutputStream
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.iterator
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

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

        val milestones = (1..20).map { it * packetCount / 20 }.distinct()

        macList.forEach { mac ->
            var nextMilestoneIndex = 0
            val key = "${sessionId}_${mac.lowercase()}"

            val captureThread = Thread {
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
                        Log.d("TCPDUMP", "Running: $command")

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

                                            if (nextMilestoneIndex < milestones.size && count >= milestones[nextMilestoneIndex]) {
                                                while (nextMilestoneIndex < milestones.size && count >= milestones[nextMilestoneIndex]) {
                                                    nextMilestoneIndex++
                                                }
                                                val progressCount = min(count, packetCount)
                                                CaptureRepository.updateDeviceCaptureProgress(sessionId, mac, progressCount, packetCount, System.currentTimeMillis(), outputFile)
                                            }

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
                    Handler(Looper.getMainLooper()).post {
                        CaptureRepository.updateDeviceCaptureState(sessionId, mac, false)
                    }
                } finally {
                    timeCaptureThreads.remove(key)
                }
            }

            captureThread.setUncaughtExceptionHandler { _, e ->
                Log.e("CAPTURE", "Error in capture thread", e)
                Handler(Looper.getMainLooper()).post {
                    CaptureRepository.updateDeviceCaptureState(sessionId, mac, false)
                }
                timeCaptureThreads.remove(key)
            }

            timeCaptureThreads[key] = captureThread
            captureThread.start()
        }
        callback(true, "Capture started for all devices")
    }


    fun captureByTime(macList: List<String>, timeLimit: Long, outputFile: String, sessionId: String, callback: (Boolean, String) -> Unit) {

        val totalSeconds = timeLimit / 1000
        val UPDATE_INTERVAL = max(1, totalSeconds / 20)

        macList.forEach { mac ->

            val key = "${sessionId}_${mac.lowercase()}"

            val captureThread = Thread {

                try {
                    val process = Runtime.getRuntime().exec("su")
                    val outputStream = DataOutputStream(process.outputStream)

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

                        Log.d("TCPDUMP", "Running: $command")

                        outputStream.writeBytes(command)
                        outputStream.writeBytes("exit\n")
                        outputStream.flush()

                        for (i in 1..timeSeconds) {

                            Thread.sleep(1000)
                            val elapsedTime = i

                            if ((i % UPDATE_INTERVAL).toInt() == 0){
                                CaptureRepository.updateDeviceCaptureProgress(sessionId, mac, elapsedTime.toInt(), timeSeconds.toInt(), System.currentTimeMillis(), outputFile)
                            }

                            Log.d("elapsedTime", "elapsedTime: $elapsedTime / $timeSeconds ")

                            sendDeviceProgressUpdate(sessionId, mac, elapsedTime.toInt(), timeSeconds.toInt(), System.currentTimeMillis(), outputFile)

                            if (elapsedTime >= timeSeconds){
                                CaptureRepository.updateCaptureState(sessionId, false)
                                CaptureRepository.updateDeviceCaptureProgress(sessionId, mac, elapsedTime.toInt(), elapsedTime.toInt(), System.currentTimeMillis(), outputFile)
                                CaptureRepository.updateDeviceCaptureState(sessionId, mac, false)
                                sendDeviceProgressUpdate(sessionId, mac, elapsedTime.toInt(), elapsedTime.toInt(), System.currentTimeMillis(), outputFile)
                                uploadPcapToFirebase(context, outputFileName, outputFile, sessionId, mac)
                            }
                        }

                        val exitCode = process.waitFor()
                        callback(exitCode == 0, outputFile)

                    } else {
                        callback(false, "IP not found")
                    }
                } catch (e: InterruptedException) {
                    Log.d("CAPTURE", "Thread interrupted for $key")
                    Handler(Looper.getMainLooper()).post {
                        CaptureRepository.updateDeviceCaptureState(sessionId, mac, false)
                    }
                } finally {
                    timeCaptureThreads.remove(key)
                }
            }

            captureThread.setUncaughtExceptionHandler { _, e ->
                Log.e("CAPTURE", "Error in capture thread", e)
                Handler(Looper.getMainLooper()).post {
                    CaptureRepository.updateDeviceCaptureState(sessionId, mac, false)
                }
                timeCaptureThreads.remove(key)
            }

            timeCaptureThreads[key] = captureThread
            captureThread.start()

        }
    }

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

    fun stopDeviceCapture(device: Device): Boolean {
        return try {

            val outputFileName = "/sdcard/${device.filename}_${device.mac}_${device.sessionId}.pcap"

            uploadPcapToFirebase(context, outputFileName, device.filename, device.sessionId, device.mac)

            val key = "${device.sessionId}_${device.mac.lowercase()}"

            val thread = timeCaptureThreads[key]

            val killProcess = Runtime.getRuntime().exec("su")
            val killOutputStream = DataOutputStream(killProcess.outputStream)

            killOutputStream.writeBytes("pkill -SIGINT -f \"tcpdump.*${device.mac}_${device.sessionId}\"\n")
            killOutputStream.writeBytes("exit\n")
            killOutputStream.flush()

            killProcess.waitFor()

            if (thread != null) {
                thread.interrupt()
                timeCaptureThreads.remove(key)
            } else {
                Log.d("STOPPED", "Thread not found")
            }

            true

        } catch (e: Exception) {
            Log.e("TCPDUMP", "Error stopping tcpdump for device ${device.mac} in session ${device.sessionId}", e)
            false
        }
    }


    private fun uploadPcapToFirebase(context: Context, filePath: String, outputFile: String, sessionId: String, mac: String) {
        val storage = FirebaseStorage.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val file = File(filePath)
        if (!file.exists()) {
            Log.e("UPLOAD", "PCAP file not found: $filePath")
            return
        }

        val MAX_FILE_SIZE_BYTES = 400 * 1024 * 1024 // 400MB
        val fileSize = file.length()

        if (fileSize > MAX_FILE_SIZE_BYTES) {
            Log.e("UPLOAD", "PCAP file too large (${fileSize / (1024 * 1024)}MB). Max allowed: ${MAX_FILE_SIZE_BYTES / (1024 * 1024)}MB")
            return // Cancel upload
        }

        var currentRetry = 0
        val maxRetries: Int = 8
        val initialDelayMs: Long = 5000
        val fileName = "pcaps/${userId}/${mac}/${outputFile}_${mac}_${sessionId}.pcap"
        val storageRef = storage.reference.child(fileName)

        fun attemptUpload() {
            storageRef.putFile(Uri.fromFile(file))
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { uri ->
                        Log.d("UPLOAD", "Upload success: ${uri.toString()}")
                        updatePcapUrlInFirestore(sessionId, mac, uri.toString())
                    }
                }
                .addOnFailureListener { exception ->
                    if (currentRetry < maxRetries) {
                        currentRetry++
                        val nextDelay = initialDelayMs * (2.0.pow(currentRetry.toDouble())).toLong()
                        Log.w("UPLOAD", "Retry $currentRetry in ${nextDelay / 1000}s...")
                        Handler(Looper.getMainLooper()).postDelayed(::attemptUpload, nextDelay)
                    } else {
                        Log.e("UPLOAD", "Max retries reached")
                    }
                }
        }

        attemptUpload()

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