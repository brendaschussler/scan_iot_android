package com.example.scaniot.model

data class CaptureSession(
    val sessionId: String,
    val timestamp: Long,
    val devices: List<Device>,
    val captureType: String, // "TIME_LIMIT" ou "PACKET_COUNT"
    val isActive: Boolean = devices.any { it.capturing }
)