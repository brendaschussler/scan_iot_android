package com.example.scaniot.model

data class Device(
    val ip: String = "",
    val mac: String = "",  // Usado as ID in Firestore
    var name: String = "unknown",
    var description: String = "unknown",
    val manufacturer: String = "unknown",
    val deviceModel: String = "unknown",
    val deviceLocation: String = "unknown",
    var photoUrl: String? = null,
    val userId: String = ""
)