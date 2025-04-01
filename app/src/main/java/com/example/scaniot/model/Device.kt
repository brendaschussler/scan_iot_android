package com.example.scaniot.model

data class Device(
    val ip: String = "",
    val mac: String = "",  // Usado como ID no Firestore
    var name: String = "unknown",
    var description: String = "unknown",
    val manufacturer: String = "unknown",
    var photoUrl: String? = null
) {
    // Construtor sem parâmetros exigido pelo Firestore
    constructor() : this("", "", "unknown", "unknown", "unknown", null)
}