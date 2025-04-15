package com.example.scaniot

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.scaniot.databinding.ActivitySavedDevicesBinding
import com.example.scaniot.model.CaptureRepository
import com.example.scaniot.model.Device
import com.example.scaniot.model.SavedDevicesAdapter
import com.example.scaniot.utils.showMessage
import com.example.scaniot.view.LoginActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class SavedDevicesActivity : AppCompatActivity() {

    companion object {
        private const val DEFAULT_PACKET_COUNT = 100
        private const val PCAP_EXTENSION = ".pcap"
    }

    private lateinit var binding: ActivitySavedDevicesBinding
    private lateinit var savedDevicesAdapter: SavedDevicesAdapter

    private val firebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    private val storage by lazy {
        FirebaseStorage.getInstance()
    }

    private val firestore by lazy {
        FirebaseFirestore.getInstance()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySavedDevicesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeToolbar()
        setupRecyclerView()
        loadSavedDevices()
        initializeClickEvents()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets

        }
    }

    private fun initializeClickEvents() {
        binding.btnStartCapture.setOnClickListener {
            val selectedDevices = savedDevicesAdapter.getSelectedDevices()

            // DEBUG: Verifique no Logcat se está capturando corretamente
            println("Dispositivos selecionados: ${selectedDevices}")
            selectedDevices.forEach { println("- ${it.name} (${it.mac})") }

            if (selectedDevices.isEmpty()) {
                showMessage("Please select at least one device")
            } else {
                showPacketCaptureDialog()
            }
        }
    }

    private fun showPacketCaptureDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_packet_capture, null)
        val editPacketCount = dialogView.findViewById<TextInputEditText>(R.id.editPacketCount)
        val editFilename = dialogView.findViewById<TextInputEditText>(R.id.editFilename)

        AlertDialog.Builder(this)
            .setTitle("Capture Settings")
            .setView(dialogView)
            .setPositiveButton("Start Capture") { _, _ ->
                val packetCount = editPacketCount.text.toString().toIntOrNull() ?: DEFAULT_PACKET_COUNT
                var filename = editFilename.text.toString().trim()

                // Garante que o arquivo termina com .pcap
                if (!filename.endsWith(PCAP_EXTENSION)) {
                    filename += PCAP_EXTENSION
                }

                //startPacketCapture(packetCount, filename)
                val selectedDevices = savedDevicesAdapter.getSelectedDevices()
                startCapturedPacketsActivity(selectedDevices, packetCount, filename)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startCapturedPacketsActivity(devices: List<Device>, packetCount: Int, filename: String) {
        // Atualiza os dispositivos com informações de captura

        val devicesWithCapture = devices.map {
            it.copy(
                capturing = true,
                captureTotal = packetCount,
                captureProgress = 0,
                lastCaptureTimestamp = System.currentTimeMillis()
            )
        }

        // Salva no Firestore
        CaptureRepository.saveLastCapture(devicesWithCapture) { success ->
            if (success) {
                val intent = Intent(this, CapturedPacketsActivity::class.java).apply {
                    putParcelableArrayListExtra("selected_devices", ArrayList(devicesWithCapture))
                    putExtra("packet_count", packetCount)
                    putExtra("filename", filename)
                }
                startActivity(intent)
            } else {
                showMessage("Failed to save capture session")
            }
        }
    }

    private fun setupRecyclerView() {
        savedDevicesAdapter = SavedDevicesAdapter(
            onDeleteClick = { device ->
                deleteDevice(device)
            },
            onCheckboxChange = { device, isChecked ->
                // Implemente a lógica de seleção aqui
                //updateSelectedDevices(device, isChecked)
            }
        )

        binding.rvListSavedDevices.apply {
            adapter = savedDevicesAdapter
            layoutManager = LinearLayoutManager(this@SavedDevicesActivity)

            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
        }
    }

    private fun loadSavedDevices() {
        val currentUser = firebaseAuth.currentUser ?: return

        firestore.collection("saved_devices")
            .document(currentUser.uid)
            .collection("devices")
            .get()
            .addOnSuccessListener { documents ->
                val devicesList = documents.map { doc ->
                    doc.toObject(Device::class.java).copy(mac = doc.id) // Usa o MAC como ID
                }
                savedDevicesAdapter.submitList(devicesList)
            }
            .addOnFailureListener { e ->
                showMessage("Error loading devices: ${e.message}")
            }
    }

    private fun deleteDevice(device: Device) {
        val currentUser = firebaseAuth.currentUser ?: return

        AlertDialog.Builder(this)
            .setTitle("Delete Device")
            .setMessage("Delete ${device.name} (${device.mac})?")
            .setPositiveButton("Delete") { _, _ ->
                firestore.collection("saved_devices")
                    .document(currentUser.uid)
                    .collection("devices")
                    .document(device.mac) // Usa o MAC como ID do documento
                    .delete()
                    .addOnSuccessListener {
                        loadSavedDevices() // Recarrega a lista
                        showMessage("Device deleted")
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun initializeToolbar() {
        val toolbar = binding.includeTbSavedDevices.tbMain
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "Saved Devices"
            setDisplayHomeAsUpEnabled(true)
        }

        binding.btnLogoutSavedDevices.setOnClickListener {
            logoutUser()
        }
    }

    private fun SavedDevicesActivity.logoutUser() {
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
