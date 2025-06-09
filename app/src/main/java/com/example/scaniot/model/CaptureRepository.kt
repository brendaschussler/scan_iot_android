package com.example.scaniot.model

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object CaptureRepository {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    fun getAllCaptureSessions(onSuccess: (List<CaptureSession>) -> Unit, onFailure: () -> Unit = {}) {
        val userId = auth.currentUser?.uid ?: run {
            onFailure()
            return
        }

        firestore.collection("captured_list")
            .document(userId)
            .collection("captures")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val sessions = mutableListOf<CaptureSession>()

                snapshot.documents.forEach { doc ->
                    val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    val sessionId = doc.id
                    val captureType = doc.getString("captureType") ?: if (doc.getLong("timeLimitMs") != null) "TIME_LIMIT" else "PACKET_COUNT"
                    val devicesMap = doc.get("devices") as? Map<String, Map<String, Any>> ?: emptyMap()
                    val isActive = doc.getBoolean("isActive") ?: false
                    val captureProgress = (doc.getLong("captureProgress") ?: 0).toInt()
                    val captureTotal = (doc.getLong("captureTotal") ?: 100).toInt()
                    val timeLimitMs = doc.getLong("timeLimitMs") ?: 0
                    val endDate = doc.getLong("endDate")
                    val filename = doc.getString("filename") ?: ""
                    val lastCaptureTimestamp = doc.getLong("lastCaptureTimestamp")

                    val devices = devicesMap.map { (mac, deviceData) ->
                        Device(
                            name = deviceData["name"] as? String ?: "",
                            mac = deviceData["mac"] as? String ?: mac,
                            captureTotal = (deviceData["captureTotal"] as? Long)?.toInt() ?: 0,
                            timeLimitMs = deviceData["timeLimitMs"] as? Long ?: 0,
                            captureProgress = (deviceData["captureProgress"] as? Long)?.toInt() ?: 0,
                            capturing = deviceData["capturing"] as? Boolean ?: false,
                            lastCaptureTimestamp = deviceData["lastCaptureTimestamp"] as? Long ?: timestamp,
                            ip = deviceData["ip"] as? String ?: "",
                            vendor = deviceData["vendor"] as? String ?: "",
                            deviceModel = deviceData["deviceModel"] as? String ?: "",
                            deviceLocation = deviceData["deviceLocation"] as? String ?: "",
                            sessionId = sessionId,
                            sessionTimestamp = timestamp,
                            filename = deviceData["filename"] as? String ?: "",
                            endDate = deviceData["endDate"] as? Long
                        )
                    }

                    sessions.add(CaptureSession(
                        sessionId = sessionId,
                        timestamp = timestamp,
                        devices = devices,
                        captureType = captureType,
                        isActive = isActive,
                        captureProgress = captureProgress,
                        captureTotal = captureTotal,
                        timeLimitMs = timeLimitMs,
                        lastCaptureTimestamp = lastCaptureTimestamp
                    ))
                }

                onSuccess(sessions)
            }
            .addOnFailureListener { onFailure() }
    }

    fun saveNewCapture(context: Context, devices: List<Device>, selectedDevices: List<Device>, packetCount: Int, timeLimitMs: Long, filename: String, onComplete: (Boolean) -> Unit = {}) {
        val userId = auth.currentUser?.uid ?: run {
            onComplete(false)
            return
        }

        val timestamp = System.currentTimeMillis()
        val sessionId = firestore.collection("captured_list").document().id

        // Initialize device states
        val devicesMap = devices.associate { device ->
            device.mac to hashMapOf(
                "name" to device.name,
                "mac" to device.mac,
                "captureTotal" to if (selectedDevices.any { it.mac == device.mac }) (if (timeLimitMs > 0) 0 else packetCount) else 0,
                "timeLimitMs" to timeLimitMs,
                "captureProgress" to 0,
                "capturing" to selectedDevices.any { it.mac == device.mac },
                "lastCaptureTimestamp" to timestamp,
                "ip" to device.ip,
                "vendor" to device.vendor,
                "deviceModel" to device.deviceModel,
                "deviceLocation" to device.deviceLocation,
                "sessionId" to sessionId,
                "sessionTimestamp" to timestamp,
                "filename" to device.filename,
                "endDate" to device.endDate
            )
        }

        val captureSession = hashMapOf(
            "timestamp" to timestamp,
            "sessionId" to sessionId,
            "devices" to devicesMap,
            "captureType" to if (timeLimitMs > 0) "TIME_LIMIT" else "PACKET_COUNT",
            "isActive" to true,
            "captureProgress" to 0,
            "captureTotal" to if (timeLimitMs > 0) 0 else packetCount,
            "timeLimitMs" to timeLimitMs,
            "lastCaptureTimestamp" to timestamp,
            "lastUpdated" to FieldValue.serverTimestamp()
        )

        firestore.collection("captured_list")
            .document(userId)
            .collection("captures")
            .document(sessionId)
            .set(captureSession)
            .addOnSuccessListener {
                if (timeLimitMs > 0) {
                    startCaptureTime(context, selectedDevices, timeLimitMs, filename, sessionId)
                } else {
                    startCapture(context, selectedDevices, packetCount, filename, sessionId)
                }
                onComplete(true)
            }
            .addOnFailureListener { onComplete(false) }
    }

    private fun startCaptureTime(
        context: Context,
        selectedDevices: List<Device>,
        timeLimit: Long,
        outputFile: String,
        sessionId: String
    ) {
        val macList = selectedDevices.map { it.mac }

        if (!checkRootAccess()) {
            showRootRequiredDialogTime(context, selectedDevices, timeLimit, outputFile, sessionId)
            return
        }

        PacketCapturer(context).captureByTime(macList, timeLimit, outputFile, sessionId) { success, message ->
            if (success)  {
                Log.d("OK", "Success")
            } else {
                Log.d("OK", "error")
            }
        }
    }

    private fun showRootRequiredDialogTime(
        context: Context,
        selectedDevices: List<Device>,
        timeLimit: Long,
        outputFile: String,
        sessionId: String
    ) {
        AlertDialog.Builder(context)
            .setTitle("Root Access Required")
            .setMessage("This feature requires superuser permissions. Please grant root access when prompted.")
            .setPositiveButton("Try Again") { _, _ ->
                if (checkRootAccess()) {
                    startCaptureTime(context, selectedDevices, timeLimit, outputFile, sessionId)
                } else {
                    showRootRequiredDialogTime(context, selectedDevices, timeLimit, outputFile, sessionId)
                }
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    private fun checkRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun showRootRequiredDialog(context: Context, selectedDevices: List<Device>, packetCount: Int, outputFile: String, sessionId: String) {
        AlertDialog.Builder(context)
            .setTitle("Root Access Required")
            .setMessage("This feature requires superuser permissions. Please grant root access when prompted.")
            .setPositiveButton("Try Again") { _, _ ->
                if (checkRootAccess()) {
                    startCapture(context, selectedDevices, packetCount, outputFile, sessionId)
                } else {
                    showRootRequiredDialog(context, selectedDevices, packetCount, outputFile, sessionId)
                }
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    private fun startCapture(context: Context, selectedDevices: List<Device>, packetCount: Int, outputFile: String, sessionId: String) {

        val macList = selectedDevices.map { it.mac }

        if (!checkRootAccess()) {
            showRootRequiredDialog(context, selectedDevices, packetCount, outputFile, sessionId)
            return
        }

        PacketCapturer(context).capture(macList, packetCount, outputFile, sessionId) { success, message ->
            if (success)  {
                Log.d("OK", "Success")
            } else {
                Log.d("OK", "error")
            }
        }
    }


    suspend fun suspendDeleteDeviceCapture(sessionId: String, mac: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext false

            firestore.collection("captured_list")
                .document(userId)
                .collection("captures")
                .document(sessionId)
                .update(
                    "devices.$mac", FieldValue.delete(),
                    "lastUpdated", FieldValue.serverTimestamp()
                ).await()

            val doc = firestore.collection("captured_list")
                .document(userId)
                .collection("captures")
                .document(sessionId)
                .get().await()

            val devices = doc.get("devices") as? Map<*, *>
            if (devices.isNullOrEmpty()) {
                doc.reference.delete().await()
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    fun updateCaptureState(sessionId: String, isActive: Boolean) {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("captured_list")
            .document(userId)
            .collection("captures")
            .document(sessionId)
            .update("isActive", isActive)
    }

    fun updateDeviceCaptureProgress(sessionId: String, mac: String, progress: Int, total: Int, endDate: Long, filename: String) {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("captured_list")
            .document(userId)
            .collection("captures")
            .document(sessionId)
            .update(
                "devices.$mac.captureProgress", progress,
                "devices.$mac.captureTotal", total,
                "devices.$mac.endDate", endDate,
                "devices.$mac.filename", filename,
                "devices.$mac.capturing", (progress < total),
                "lastUpdated", FieldValue.serverTimestamp(),
            )
    }

    fun updateDeviceCaptureState(sessionId: String, mac: String, isActive: Boolean) {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("captured_list")
            .document(userId)
            .collection("captures")
            .document(sessionId)
            .update(
                "devices.$mac.capturing", isActive,
                "lastUpdated", FieldValue.serverTimestamp()
            )
    }

}