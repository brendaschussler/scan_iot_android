package com.example.scaniot.helper

import android.app.Activity
import androidx.core.app.ActivityCompat

class Permissions {

    companion object{

        fun myRequestPermissions(activity: Activity, myPermissions: List<String>){
            ActivityCompat.requestPermissions(activity, myPermissions.toTypedArray(), 0)
        }
    }
}