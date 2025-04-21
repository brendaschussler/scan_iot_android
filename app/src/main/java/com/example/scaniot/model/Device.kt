package com.example.scaniot.model

import android.os.Parcelable
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Device(
    val ip: String = "",
    val mac: String = "",
    var name: String = "unknown",
    var description: String = "unknown",
    var vendor: String = "unknown",
    var deviceModel: String = "unknown",
    var deviceType: String = "unknown",
    var deviceVersion: String = "unknown",
    var deviceLocation: String = "unknown",
    var photoUrl: String? = null,
    val userId: String = "",
    val capturing: Boolean = false,
    val captureProgress: Int = 0,
    val captureTotal: Int = 100,
    val lastCaptureTimestamp: Long? = null,
    var isSaved: Boolean = false,
    var isNew: Boolean = false,
    val sessionId: String = "",
    val sessionTimestamp: Long = 0L
) : Parcelable