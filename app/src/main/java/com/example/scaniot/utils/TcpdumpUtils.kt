package com.example.scaniot.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.DataOutputStream

object TcpdumpUtils {

    fun checkTcpdumpAvailable(context: Context, callback: (Boolean) -> Unit) {
        Thread {
            try {

                val process = Runtime.getRuntime().exec("su")
                val outputStream = DataOutputStream(process.outputStream)
                val inputStream = process.inputStream.bufferedReader()

                outputStream.apply {
                    writeBytes("tcpdump --version >/dev/null 2>&1 && echo '1' || echo '0'\n")
                    writeBytes("exit\n")
                    flush()
                    close()
                }

                val result = inputStream.readLine()?.trim() == "1"
                val exitCode = process.waitFor()

                Handler(Looper.getMainLooper()).post {
                    if (exitCode == 0 && result) {
                        callback(true)
                    } else {
                        Toast.makeText(
                            context,
                            "Tcpdump command not available, please install following the tutorial in the help icon",
                            Toast.LENGTH_LONG
                        ).show()
                        callback(false)
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "Verification error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    callback(false)
                }
            }
        }.start()
    }

    fun checkTimeoutInstalled(context: Context, callback: (Boolean) -> Unit) {
        Thread {
            try {

                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "which timeout || command -v timeout"))
                val isInstalled = process.waitFor() == 0

                Handler(Looper.getMainLooper()).post {
                    if (isInstalled) {
                        callback(true)
                    } else {
                        Toast.makeText(
                            context,
                            "Timeout command not available, please install following the tutorial in the help icon",
                            Toast.LENGTH_LONG
                        ).show()
                        callback(false)
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    callback(false)
                }
            }
        }.start()
    }


}