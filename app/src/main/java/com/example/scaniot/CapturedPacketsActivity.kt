package com.example.scaniot

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.scaniot.databinding.ActivityCapturedPacketsBinding
import com.example.scaniot.model.CaptureRepository
import com.example.scaniot.LoginActivity
import com.example.scaniot.model.CapturedPacketsAdapter
import com.example.scaniot.model.Device
import com.google.firebase.auth.FirebaseAuth

class CapturedPacketsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCapturedPacketsBinding
    private lateinit var capturedPacketsAdapter: CapturedPacketsAdapter

    private val firebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityCapturedPacketsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeToolbar()
        setupRecyclerView()
        loadSelectedDevices()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    fun Context.showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun setupRecyclerView() {
        capturedPacketsAdapter = CapturedPacketsAdapter()
        binding.rvListCapturedPackets.apply {
            adapter = capturedPacketsAdapter
            layoutManager = LinearLayoutManager(this@CapturedPacketsActivity)

            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
        }
    }

    private fun loadSelectedDevices() {
        loadAllCapturedDevices()
    }

    private fun loadCapturedDevices() {
        val forceRefresh = intent.getBooleanExtra("force_refresh", false)

        if (forceRefresh || intent.getParcelableArrayListExtra<Device>("selected_devices") == null) {
            // Carrega todo o histórico de capturas
            loadAllCapturedDevices()
        } else {
            // Usa os dispositivos do intent (quando vem da SavedDevices)
            val devices = intent.getParcelableArrayListExtra<Device>("selected_devices")!!
            capturedPacketsAdapter.submitList(devices)
        }
    }

    private fun loadAllCapturedDevices() {
        CaptureRepository.getAllCapturedDevices(
            onSuccess = { devices ->
                // Ordena por timestamp decrescente e agrupa por sessão
                val groupedDevices = devices
                    .groupBy { it.sessionId }
                    .flatMap { (_, sessionDevices) ->
                        // Ordena dispositivos dentro de cada sessão
                        sessionDevices.sortedByDescending { it.lastCaptureTimestamp }
                    }
                    .sortedByDescending { it.lastCaptureTimestamp }

                capturedPacketsAdapter.submitList(groupedDevices)
            },
            onFailure = {
                showMessage("Failed to load capture history")
                finish()
            }
        )
    }


    private fun initializeToolbar() {
        val toolbar = binding.includeTbCapturedPackets.tbMain
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "Captured Packets"
            setDisplayHomeAsUpEnabled(true)
        }

        binding.btnLogoutCapturedPackets.setOnClickListener {
            logoutUser()
        }
    }

    private fun logoutUser() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to log out")
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("Yes") { _, _ ->
                firebaseAuth.signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .create()
            .show()
    }
}