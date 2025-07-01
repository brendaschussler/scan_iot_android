package com.example.scaniot.model

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

object MacVendorResolver {
    private const val TAG = "VendorLookup"
    private const val API_BASE_URL = "https://api.maclookup.app/v2/macs"
    private val client = OkHttpClient()
    private val cache = mutableMapOf<String, String>()

    suspend fun getVendor(mac: String): String = withContext(Dispatchers.IO) {
        val normalizedMac = mac.trim().lowercase()
        cache[normalizedMac]?.let {
            Log.d("MAC", "Cache hit for $normalizedMac: $it")
            return@withContext it
        }

        val url = "$API_BASE_URL/$normalizedMac"
        Log.d("MAC", "Request URL: $url")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "ScanIOTApp")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                Log.d("MAC", "Response code for $normalizedMac: ${response.code}")

                if (!response.isSuccessful) {
                    return@withContext when (response.code) {
                        404 -> "Vendor not found"
                        429 -> {
                            Log.w("MAC", "Rate limit exceeded for $normalizedMac")
                            "Rate limit exceeded"
                        }
                        else -> "Unknown Vendor"
                    }
                }

                val body = response.body?.string()
                if (body.isNullOrEmpty()) {
                    Log.w("MAC", "Empty response body for $normalizedMac")
                    return@withContext "Unknown Vendor"
                }

                var company = JSONObject(body).optString("company", "Unknown Vendor")
                Log.d("MAC", "Vendor found: $company")
                if (company=="" || company==" " || company==null){
                    Log.d("MAC", "Vendor found: Unknown Vendor")
                    company = "Unknown Vendor"
                }
                cache[normalizedMac] = company
                return@withContext company
            }
        } catch (e: IOException) {
            Log.e("MAC", "Network error for $normalizedMac: ${e.message}")
            return@withContext "Network Error"
        } catch (e: Exception) {
            Log.e("MAC", "Unexpected error: ${e.message}")
            return@withContext "Unknown Vendor"
        }
    }
}
