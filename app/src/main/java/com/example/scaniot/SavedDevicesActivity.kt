package com.example.scaniot

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioGroup
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
import com.example.scaniot.LoginActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
        val radioCaptureMode = dialogView.findViewById<RadioGroup>(R.id.radioCaptureMode)
        val layoutCaptureValue = dialogView.findViewById<TextInputLayout>(R.id.layoutCaptureValue)
        val editCaptureValue = dialogView.findViewById<TextInputEditText>(R.id.editCaptureValue)
        val editFilename = dialogView.findViewById<TextInputEditText>(R.id.editFilename)

        // Configura o listener para mudar o modo
        radioCaptureMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioPacketCount -> {
                    layoutCaptureValue.hint = "Number of packets"
                    editCaptureValue.inputType = InputType.TYPE_CLASS_NUMBER
                    editCaptureValue.setText("100")
                }
                R.id.radioTimeLimit -> {
                    layoutCaptureValue.hint = "Time limit (hours)"
                    editCaptureValue.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    editCaptureValue.setText("3.0")
                }
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Capture Settings")
            .setView(dialogView)
            .setPositiveButton("Start Capture") { _, _ ->
                var filename = editFilename.text.toString().trim()
                if (!filename.endsWith(PCAP_EXTENSION)) {
                    filename += PCAP_EXTENSION
                }

                val selectedDevices = savedDevicesAdapter.getSelectedDevices()

                if (radioCaptureMode.checkedRadioButtonId == R.id.radioPacketCount) {
                    val packetCount = editCaptureValue.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_PACKET_COUNT
                    startCapturedPacketsActivity(selectedDevices, packetCount, 0, filename)
                } else {
                    // Converte tanto ponto quanto vírgula para double
                    val hoursText = editCaptureValue.text.toString()
                        .replace(',', '.') // Substitui vírgula por ponto
                    val hours = hoursText.toDoubleOrNull()?.coerceAtLeast(0.1) ?: 3.0
                    val milliseconds = (hours * 3600 * 1000).toLong()
                    startCapturedPacketsActivity(selectedDevices, 0, milliseconds, filename)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startCapturedPacketsActivity(devices: List<Device>, packetCount: Int, timeLimitMs: Long, filename: String) {
        val devicesWithCapture = devices.map {
            it.copy(
                capturing = true,
                captureTotal = if (timeLimitMs > 0) 0 else packetCount.coerceAtLeast(1), // Garante pelo menos 1 pacote
                timeLimitMs = timeLimitMs,
                captureProgress = 0,
                lastCaptureTimestamp = System.currentTimeMillis()
            )
        }

        CaptureRepository.saveNewCapture(devicesWithCapture) { success ->
            if (success) {
                val intent = Intent(this, CapturedPacketsActivity::class.java).apply {
                    putParcelableArrayListExtra("selected_devices", ArrayList(devicesWithCapture))
                    putExtra("packet_count", packetCount)
                    putExtra("time_limit_ms", timeLimitMs)
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