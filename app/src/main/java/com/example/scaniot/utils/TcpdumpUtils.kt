package com.example.scaniot.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.DataOutputStream

object TcpdumpUtils {

    private val TERMUX_ERROR_PATTERNS = listOf(
        Regex("no daemon is currently running", RegexOption.IGNORE_CASE),
        Regex("failed to bind to daemon", RegexOption.IGNORE_CASE),
        Regex("connection refused|cannot connect to daemon", RegexOption.IGNORE_CASE),
        Regex("daemon not running", RegexOption.IGNORE_CASE),
        Regex("termux-api is not running", RegexOption.IGNORE_CASE)
    )

    fun checkForTermuxErrors(errorOutput: String): Boolean {
        return TERMUX_ERROR_PATTERNS.any { it.containsMatchIn(errorOutput) }
    }

    fun checkTcpdumpAvailable(context: Context, callback: (Boolean) -> Unit) {
        Thread {
            try {

                val process = Runtime.getRuntime().exec("su")
                val outputStream = DataOutputStream(process.outputStream)
                val inputStream = process.inputStream.bufferedReader()
                val errorStream = process.errorStream.bufferedReader()

                outputStream.apply {
                    writeBytes("tcpdump --version >/dev/null 2>&1 && echo '1' || echo '0'\n")
                    writeBytes("exit\n")
                    flush()
                    close()
                }

                val errorOutput = errorStream.readText().trim()

                if (checkForTermuxErrors(errorOutput)) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            context,
                            "Termux system error: Please restart your device",
                            Toast.LENGTH_LONG
                        ).show()
                        callback(false)
                    }
                    return@Thread
                }

                val result = inputStream.readLine()?.trim() == "1"
                val exitCode = process.waitFor()

                Handler(Looper.getMainLooper()).post {
                    if (exitCode == 0 && result) {
                        callback(true)
                    } else {
                        Toast.makeText(
                            context,
                            "Tcpdump command not available, please install following the tutorial in the system requirements tab",
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
                            "Timeout command not available, please install following the tutorial in the system requirements tab",
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