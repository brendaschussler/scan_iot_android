package com.example.scaniot

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Toast
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
import com.example.scaniot.model.PacketCapturer

class SavedDevicesActivity : AppCompatActivity() {

    companion object {
        private const val DEFAULT_PACKET_COUNT = 1000
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
        val timeInputLayout = dialogView.findViewById<LinearLayout>(R.id.timeInputLayout)

        // Campos de tempo
        val editHours = dialogView.findViewById<TextInputEditText>(R.id.editHours)
        val editMinutes = dialogView.findViewById<TextInputEditText>(R.id.editMinutes)
        val editSeconds = dialogView.findViewById<TextInputEditText>(R.id.editSeconds)

        // Configura o listener para mudar o modo
        radioCaptureMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioPacketCount -> {
                    layoutCaptureValue.visibility = View.VISIBLE
                    timeInputLayout.visibility = View.GONE
                    layoutCaptureValue.hint = "Number of packets"
                    editCaptureValue.inputType = InputType.TYPE_CLASS_NUMBER
                    editCaptureValue.setText("1000")
                }
                R.id.radioTimeLimit -> {
                    layoutCaptureValue.visibility = View.GONE
                    timeInputLayout.visibility = View.VISIBLE
                }
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Capture Settings")
            .setView(dialogView)
            .setPositiveButton("Start Capture") { _, _ ->
                var filename = editFilename.text.toString().trim()
                val selectedDevices = savedDevicesAdapter.getSelectedDevices()

                if (radioCaptureMode.checkedRadioButtonId == R.id.radioPacketCount) {
                    val packetCount = editCaptureValue.text.toString().toIntOrNull() ?: 0
                    if (packetCount == 0) {
                        showMessage("Please set at least some time for capture")
                        return@setPositiveButton
                    }
                    startCapturedPacketsActivity(selectedDevices, packetCount, 0, filename)
                } else {
                    // Obter valores de horas, minutos e segundos (considerar 0 se vazio)
                    val hours = editHours.text.toString().toIntOrNull() ?: 0
                    val minutes = editMinutes.text.toString().toIntOrNull() ?: 0
                    val seconds = editSeconds.text.toString().toIntOrNull() ?: 0

                    // Validar pelo menos algum tempo foi definido
                    if (hours == 0 && minutes == 0 && seconds == 0) {
                        showMessage("Please set at least some time for capture")
                        return@setPositiveButton
                    }

                    val totalSeconds = (hours * 3600) + (minutes * 60) + seconds
                    if (totalSeconds > 2_147_483) {
                        Toast.makeText(this, "Total time exceeds maximum limit (596 hours)", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    // Converter para milissegundos
                    val milliseconds = totalSeconds * 1000L
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

        CaptureRepository.saveNewCapture(this, devicesWithCapture, devices, packetCount, timeLimitMs, filename) { success ->
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