package com.example.scaniot.model

data class Device(
    val ip: String = "",
    val mac: String = "",  // Usado as ID in Firestore
    var name: String = "unknown",
    var description: String = "unknown",
    var vendor: String = "unknown",
    var deviceModel: String = "unknown",
    var deviceLocation: String = "unknown",
    var photoUrl: String? = null,
    val userId: String = ""
)