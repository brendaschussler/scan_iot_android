package com.example.scaniot.model

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class RootManager(private val context: Context) {
    private val preferences = context.getSharedPreferences("root_prefs", Context.MODE_PRIVATE)
    private var rootAccess: Boolean? = null

    // Verifica se o dispositivo está rootado
    fun isDeviceRooted(): Boolean {
        val paths = arrayOf("/system/bin/su", "/system/xbin/su", "/sbin/su")
        return paths.any { File(it).exists() }
    }

    // Verifica se já temos permissão root (com cache)
    suspend fun hasRootAccess(): Boolean {
        return rootAccess ?: run {
            val access = checkRootAccess()
            rootAccess = access
            access
        }
    }

    suspend fun installRequiredTools(): Boolean = withContext(Dispatchers.IO) {
        try {
            val commands = arrayOf(
                "apt update",
                "apt install -y nmblookup nbtscan samba-common-bin",
                "busybox --install /system/xbin",
                "ln -s /system/xbin/busybox /system/xbin/nbtscan"
            )

            commands.forEach { command ->
                executeRootCommand(command, 30000) ?: throw Exception("Falha no comando: $command")
            }
            true
        } catch (e: Exception) {
            Log.e("RootManager", "Erro instalando ferramentas", e)
            false
        }
    }

    // Verificação real de acesso root
    private suspend fun checkRootAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = executeRootCommand("id")
            result?.contains("uid=0") ?: false
        } catch (e: Exception) {
            false
        }
    }

    // Executa comando com tratamento de timeout
    suspend fun executeRootCommand(command: String, timeout: Long = 5000): String? {
        return try {
            withTimeout(timeout) {
                Runtime.getRuntime().exec(arrayOf("su", "-c", command)).let { process ->
                    val output = process.inputStream.bufferedReader().use { it.readText() }
                    process.waitFor()
                    if (process.exitValue() == 0) output else null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    // Verifica se o usuário já negou permanentemente
    fun wasRootDenied(): Boolean {
        return preferences.getBoolean("root_denied", false)
    }

    // Armazena a negação permanente
    fun setRootDenied(denied: Boolean) {
        preferences.edit().putBoolean("root_denied", denied).apply()
    }

    fun installNetworkToolsIfNeeded() {
        if (!isDeviceRooted()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Verifica se as ferramentas já estão instaladas
                if (!isToolInstalled("nmblookup") || !isToolInstalled("nbtscan")) {
                    executeRootCommand("apt update && apt install -y nmblookup nbtscan samba-common-bin", timeout = 60000)
                    Log.d("RootManager", "Ferramentas de rede instaladas com sucesso")
                }
            } catch (e: Exception) {
                Log.e("RootManager", "Erro ao instalar ferramentas", e)
            }
        }
    }

    // Verifica se as ferramentas estão instaladas
    fun areToolsInstalled(): Boolean {
        return try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "which nmblookup")).waitFor() == 0 &&
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "which nbtscan")).waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun isToolInstalled(toolName: String): Boolean {
        return executeRootCommand("which $toolName") != null
    }
}