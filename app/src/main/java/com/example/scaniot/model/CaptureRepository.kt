package com.example.scaniot.model

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

object CaptureRepository {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    // Salva uma nova captura no histórico
    fun saveNewCapture(devices: List<Device>, onComplete: (Boolean) -> Unit = {}) {
        val userId = auth.currentUser?.uid ?: run {
            onComplete(false)
            return
        }

        val timestamp = System.currentTimeMillis()
        val sessionId = firestore.collection("captured_list").document().id

        val batch = firestore.batch()
        val collectionRef = firestore.collection("captured_list")
            .document(userId)
            .collection("captures")
            .document() // Gera um novo ID automático para esta sessão de captura


        val devicesMap = devices.associate { device ->
            device.mac to hashMapOf(
                "name" to device.name,
                "mac" to device.mac,
                "captureTotal" to device.captureTotal,
                "captureProgress" to device.captureProgress,
                "capturing" to device.capturing,
                "lastCaptureTimestamp" to device.lastCaptureTimestamp,
                // Adicione outros campos necessários
                "ip" to device.ip,
                "vendor" to device.vendor,
                "deviceModel" to device.deviceModel,
                "deviceLocation" to device.deviceLocation
            )
        }

        val captureSession = hashMapOf(
            "timestamp" to timestamp,
            "devices" to devicesMap
        )

        firestore.collection("captured_list")
            .document(userId)
            .collection("captures")
            .document(sessionId)
            .set(captureSession)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun getAllCapturedDevices(onSuccess: (List<Device>) -> Unit, onFailure: () -> Unit = {}) {
        val userId = auth.currentUser?.uid ?: run {
            onFailure()
            return
        }

        firestore.collection("captured_list")
            .document(userId)
            .collection("captures")
            .get()
            .addOnSuccessListener { snapshot ->
                val allDevices = mutableListOf<Device>()

                snapshot.documents.forEach { doc ->
                    val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    val devicesMap = doc.get("devices") as? Map<String, Map<String, Any>> ?: emptyMap()

                    devicesMap.forEach { (mac, deviceData) ->
                        allDevices.add(
                            Device(
                                name = deviceData["name"] as? String ?: "",
                                mac = deviceData["mac"] as? String ?: "",
                                captureTotal = (deviceData["captureTotal"] as? Long)?.toInt() ?: 0,
                                captureProgress = (deviceData["captureProgress"] as? Long)?.toInt() ?: 0,
                                capturing = deviceData["capturing"] as? Boolean ?: false,
                                lastCaptureTimestamp = deviceData["lastCaptureTimestamp"] as? Long ?: timestamp,
                                ip = deviceData["ip"] as? String ?: "",
                                vendor = deviceData["vendor"] as? String ?: "",
                                deviceModel = deviceData["deviceModel"] as? String ?: "",
                                deviceLocation = deviceData["deviceLocation"] as? String ?: "",
                                sessionId = doc.id,
                                sessionTimestamp = timestamp // Adicionamos este novo campo
                            )

                        )
                    }
                }

                onSuccess(allDevices)
            }
            .addOnFailureListener { onFailure() }
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

    fun deleteDeviceFromCapture(sessionId: String, deviceMac: String, onComplete: (Boolean) -> Unit) {
        val userId = auth.currentUser?.uid ?: run {
            onComplete(false)
            return
        }

        firestore.collection("captured_list")
            .document(userId)
            .collection("captures")
            .document(sessionId)
            .update("devices.$deviceMac", FieldValue.delete())
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