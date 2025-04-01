package com.example.scaniot

import com.example.scaniot.model.Device
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class DeviceRepository {
    private val db = FirebaseFirestore.getInstance()
    private val devicesRef = db.collection("devices")

    // Salva ou atualiza um dispositivo no Firestore
    suspend fun saveDevice(device: Device) {
        try {
            // Usa o MAC como ID do documento para garantir unicidade
            devicesRef.document(device.mac)
                .set(device)
                .await() // Suspende até a operação completar
        } catch (e: Exception) {
            throw DeviceSaveException("Failed to save device", e)
        }
    }

    // Busca dispositivos salvos
    suspend fun getSavedDevices(): List<Device> {
        return try {
            devicesRef.get().await().toObjects(Device::class.java)
        } catch (e: Exception) {
            throw DeviceFetchException("Failed to fetch devices", e)
        }
    }
}

class DeviceSaveException(message: String, cause: Throwable) : Exception(message, cause)
class DeviceFetchException(message: String, cause: Throwable) : Exception(message, cause)