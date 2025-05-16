// CapturedPacketsActivity.kt
package com.example.scaniot

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import com.example.scaniot.model.CaptureSession
import com.example.scaniot.model.Device
import com.example.scaniot.model.PacketCapturer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CapturedPacketsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCapturedPacketsBinding
    private lateinit var capturedPacketsAdapter: CapturedPacketsAdapter

    private val firebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                PacketCapturer.PROGRESS_UPDATE_ACTION -> {
                    val sessionId = intent.getStringExtra(PacketCapturer.EXTRA_SESSION_ID)
                    val mac = intent.getStringExtra("mac") ?: return
                    val progress = intent.getIntExtra(PacketCapturer.EXTRA_PROGRESS, 0)
                    val total = intent.getIntExtra(PacketCapturer.EXTRA_TOTAL, 100)
                    val end = intent.getLongExtra(PacketCapturer.EXTRA_END, System.currentTimeMillis())
                    val filename = intent.getStringExtra(PacketCapturer.EXTRA_FILENAME) ?: return

                    // Update the specific device in the list
                    val currentList = capturedPacketsAdapter.currentList.toMutableList()
                    val position = currentList.indexOfFirst {
                        it.mac == mac && it.sessionId == sessionId
                    }

                    if (position != -1) {
                        val device = currentList[position].copy(
                            captureProgress = progress,
                            captureTotal = total,
                            endDate = end,
                            filename = filename,
                            capturing = progress < total
                        )
                        currentList[position] = device
                        capturedPacketsAdapter.submitList(currentList)

                    }

                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityCapturedPacketsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        loadCaptureSessions()

        val filter = IntentFilter(PacketCapturer.PROGRESS_UPDATE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(progressReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(progressReceiver)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }
    }

    private fun refreshData(showLoading: Boolean = true) {
        if (showLoading) {
            binding.swipeRefreshLayout.isRefreshing = true
        }

        CaptureRepository.getAllCaptureSessions(
            onSuccess = { sessions ->
                // Flatten the list of devices from all sessions
                val allDevices = sessions.flatMap { session ->
                    session.devices.map { device -> device.copy(sessionId = session.sessionId) }
                }
                updateDeviceList(allDevices)
                binding.swipeRefreshLayout.isRefreshing = false
            },
            onFailure = {
                showMessage("Failed to refresh data")
                binding.swipeRefreshLayout.isRefreshing = false
            }
        )
    }

    private fun updateDeviceList(newDevices: List<Device>) {
        capturedPacketsAdapter.submitList(newDevices) {
            binding.rvListCapturedPackets.scrollToPosition(0)
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
        }
    }

    private fun loadCaptureSessions() {
        refreshData()
    }

    private fun initializeToolbar() {
        val toolbar = binding.includeTbCapturedPackets.tbMain
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "Capture Sessions"
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