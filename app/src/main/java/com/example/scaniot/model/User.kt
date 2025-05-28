package com.example.scaniot.model

data class User (
    var userId: String,
    var name: String,
    var lastName: String,
    var country: String,
    var email: String,
    var organization: String? = null,
    var jobTitle: String? = null,
    var manager: String? = null
)