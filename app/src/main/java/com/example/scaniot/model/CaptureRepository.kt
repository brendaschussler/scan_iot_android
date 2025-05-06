package com.example.scaniot.model

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

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
                            sessionTimestamp = timestamp
                        )
                    }

                    sessions.add(CaptureSession(
                        sessionId = sessionId,
                        timestamp = timestamp,
                        devices = devices,
                        captureType = captureType,
                        isActive = devices.any { it.capturing }
                    ))
                }

                onSuccess(sessions)
            }
            .addOnFailureListener { onFailure() }
    }

    fun saveNewCapture(context: Context, devices: List<Device>, selectedDevices: List<Device>, packetCount: Int, filename : String, onComplete: (Boolean) -> Unit = {}) {
        startCapture(context, selectedDevices, packetCount, filename)

        val userId = auth.currentUser?.uid ?: run {
            onComplete(false)
            return
        }

        val timestamp = System.currentTimeMillis()
        val sessionId = firestore.collection("captured_list").document().id

        val devicesMap = devices.associate { device ->
            device.mac to hashMapOf(
                "name" to device.name,
                "mac" to device.mac,
                "captureTotal" to device.captureTotal,
                "timeLimitMs" to device.timeLimitMs,  // Novo campo adicionado
                "captureProgress" to device.captureProgress,
                "capturing" to device.capturing,
                "lastCaptureTimestamp" to device.lastCaptureTimestamp,
                "ip" to device.ip,
                "vendor" to device.vendor,
                "deviceModel" to device.deviceModel,
                "deviceLocation" to device.deviceLocation,
                "sessionId" to sessionId,  // Adicionando sessionId a cada dispositivo
                "sessionTimestamp" to timestamp
            )
        }

        val captureSession = hashMapOf(
            "timestamp" to timestamp,
            "sessionId" to sessionId,
            "devices" to devicesMap,
            "captureType" to if (devices.any { it.timeLimitMs > 0 }) "TIME_LIMIT" else "PACKET_COUNT"  // Novo campo para tipo de captura
        )

        firestore.collection("captured_list")
            .document(userId)
            .collection("captures")
            .document(sessionId)
            .set(captureSession)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
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

    private fun showRootRequiredDialog(context: Context, selectedDevices: List<Device>, packetCount: Int, outputFile: String) {
        AlertDialog.Builder(context)
            .setTitle("Acesso Root Necessário")
            .setMessage("Este recurso requer permissões de superusuário. Por favor, conceda o acesso root quando solicitado.")
            .setPositiveButton("Tentar Novamente") { _, _ ->
                if (checkRootAccess()) {
                    startCapture(context, selectedDevices, packetCount, outputFile)
                } else {
                    showRootRequiredDialog(context, selectedDevices, packetCount, outputFile)
                }
            }
            .setNegativeButton("Cancelar", null)
            .setCancelable(false)
            .show()
    }

    private fun startCapture(context: Context, selectedDevices: List<Device>, packetCount: Int, outputFile: String) {

        val macList = selectedDevices.map { it.mac }
        //val packetCount = 10000
        val outputFileName = "/sdcard/${outputFile}.pcap"

        if (!checkRootAccess()) {
            showRootRequiredDialog(context, selectedDevices, packetCount, outputFile)
            return
        }

        /*val selectedDevices = savedDevicesAdapter.getSelectedDevices()
        if (selectedDevices.isEmpty()) {
            showMessage("Selecione dispositivos")
            return
        }*/

        PacketCapturer(context).capture(macList, packetCount, outputFileName) { success, message ->
            if (success)  {
                Log.d("OK", "Success, iniciando captura")
            } else {
                Log.d("OK", "error")
            }
        }
    }



    // Atualiza o progresso em uma captura específica
    fun updateCaptureProgress(sessionId: String, device: Device, progress: Int, total: Int) {
        val userId = auth.currentUser?.uid ?: return

        val devicePath = "devices.${device.mac}"

        firestore.collection("captured_list")
            .document(userId)
            .collection("captures")
            .document(sessionId)
            .update(
                "$devicePath.capturing", progress < total,
                "$devicePath.captureProgress", progress,
                "$devicePath.captureTotal", total,
                "$devicePath.lastCaptureTimestamp", System.currentTimeMillis()
            )
    }

    fun deleteCaptureSession(sessionId: String, onComplete: (Boolean) -> Unit) {
        val userId = auth.currentUser?.uid ?: run {
            onComplete(false)
            return
        }

        firestore.collection("captured_list")
            .document(userId)
            .collection("captures")
            .document(sessionId)
            .delete()
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun updateCaptureState(sessionId: String, device: Device, isCapturing: Boolean) {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("captured_list")
            .document(userId)
            .collection("captures")
            .document(sessionId)
            .update("devices.${device.mac}.capturing", isCapturing)
    }


}