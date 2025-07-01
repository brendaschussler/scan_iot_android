package com.example.scaniot.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object RootUtils {

    fun checkRootAccess(context: Context, callback: (Boolean) -> Unit) {
        Thread {
            val isRootAvailable = isRootAvailable()
            if (!isRootAvailable) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Root not available on this device", Toast.LENGTH_LONG).show()
                    callback(false)
                }
                return@Thread
            }

            val isRootGranted = isRootGranted()
            if (!isRootGranted) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Root permission not granted", Toast.LENGTH_LONG).show()
                    callback(false)
                }
                return@Thread
            }

            Handler(Looper.getMainLooper()).post {
                callback(true)
            }
        }.start()
    }

    private fun isRootAvailable(): Boolean {
        return try {
            Runtime.getRuntime().exec("su").destroy()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isRootGranted(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            val inputStream = BufferedReader(InputStreamReader(process.inputStream))

            outputStream.apply {
                writeBytes("id\n")
                flush()
                writeBytes("exit\n")
                flush()
                close()
            }

            process.waitFor()
            inputStream.use { it.readText().contains("uid=0") }
        } catch (e: Exception) {
            false
        }
    }
}