package com.example.scaniot.model

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// CaptureRepository.kt
object CaptureRepository {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    fun saveLastCapture(devices: List<Device>, onComplete: (Boolean) -> Unit = {}) {
        val userId = auth.currentUser?.uid ?: run {
            onComplete(false)
            return
        }

        val batch = firestore.batch()
        val collectionRef = firestore.collection("users").document(userId).collection("last_capture")

        // Limpa captura anterior
        collectionRef.get().addOnSuccessListener { snapshot ->
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }

            // Adiciona novos dispositivos
            devices.forEachIndexed { index, device ->
                val docRef = collectionRef.document("device_$index")
                batch.set(docRef, device)
            }

            batch.commit()
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { onComplete(false) }
        }
    }

    fun getLastCapture(onSuccess: (List<Device>) -> Unit, onFailure: () -> Unit = {}) {
        val userId = auth.currentUser?.uid ?: run {
            onFailure()
            return
        }

        firestore.collection("users")
            .document(userId)
            .collection("last_capture")
            .get()
            .addOnSuccessListener { snapshot ->
                val devices = snapshot.documents.mapNotNull { it.toObject(Device::class.java) }
                onSuccess(devices)
            }
            .addOnFailureListener { onFailure() }
    }

    fun updateCaptureState(device: Device, isCapturing: Boolean) {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("users")
            .document(userId)
            .collection("last_capture")
            .whereEqualTo("mac", device.mac)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.documents.firstOrNull()?.reference?.update("capturing", isCapturing)
            }
    }

    fun updateCaptureProgress(device: Device, progress: Int, total: Int) {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("users")
            .document(userId)
            .collection("last_capture")
            .whereEqualTo("mac", device.mac)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val docId = snapshot.documents[0].id
                    firestore.collection("users")
                        .document(userId)
                        .collection("last_capture")
                        .document(docId)
                        .update(
                            "isCapturing", progress < total,
                            "captureProgress", progress,
                            "captureTotal", total,
                            "lastCaptureTimestamp", System.currentTimeMillis()
                        )
                }
            }
    }
}