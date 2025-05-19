package com.example.scaniot.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.DataOutputStream

object TcpdumpUtils {

    /**
     * Verifica se o tcpdump está acessível no mesmo contexto da captura
     */
    fun checkTcpdumpAvailable(context: Context, callback: (Boolean) -> Unit) {
        Thread {
            try {
                // Executa no mesmo contexto que sua captura (via su)
                val process = Runtime.getRuntime().exec("su")
                val outputStream = DataOutputStream(process.outputStream)
                val inputStream = process.inputStream.bufferedReader()

                // Comando que verifica o tcpdump
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
                            "tcpdump não disponível no ambiente root",
                            Toast.LENGTH_LONG
                        ).show()
                        callback(false)
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "Erro na verificação: ${e.message}",
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
                // Verifica se o timeout está disponível
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "which timeout || command -v timeout"))
                val isInstalled = process.waitFor() == 0

                Handler(Looper.getMainLooper()).post {
                    if (isInstalled) {
                        callback(true)
                    } else {
                        Toast.makeText(
                            context,
                            "timeout não disponível no ambiente root",
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