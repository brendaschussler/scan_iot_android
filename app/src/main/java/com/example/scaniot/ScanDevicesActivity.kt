package com.example.scaniot

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.scaniot.databinding.ActivityScanDevicesBinding
import com.example.scaniot.model.Device
import com.example.scaniot.model.ScanDevicesAdapter
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class ScanDevicesActivity : AppCompatActivity() {

    private lateinit var scanDevicesAdapter: ScanDevicesAdapter
    private lateinit var binding: ActivityScanDevicesBinding

    private val firebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    private val storage by lazy {
        FirebaseStorage.getInstance()
    }

    private val firestore by lazy {
        FirebaseFirestore.getInstance()
    }

    val currentUserId = firebaseAuth.currentUser?.uid ?: ""
    val fakeDevices = listOf(
        Device(
            ip = "192.168.1.101",
            mac = "00:1A:2B:3C:4D:5E",
            name = "Smart TV",
            description = "Samsung 4K UHD",
            manufacturer = "Samsung",
            userId = currentUserId
        ),
        Device(
            ip = "192.168.1.102",
            mac = "00:1B:2C:3D:4E:5F",
            name = "Smartphone",
            description = "Android Phone",
            manufacturer = "Xiaomi",
            userId = currentUserId
        ),
        Device(
            ip = "192.168.1.103",
            mac = "00:1C:2D:3E:4F:5A",
            name = "Notebook",
            description = "Work laptop",
            manufacturer = "Dell",
            userId = currentUserId
        ),
        Device(
            ip = "192.168.1.104",
            mac = "00:1D:2E:3F:4A:5B",
            name = "Smart Light",
            description = "RGB Bulb",
            manufacturer = "Philips",
            userId = currentUserId
        ),
        Device(
            ip = "192.168.1.105",
            mac = "00:1E:2F:3A:4B:5C",
            name = "Security Camera",
            description = "Outdoor camera",
            manufacturer = "TP-Link",
            userId = currentUserId
        ),
        Device(
            ip = "192.168.1.105",
            mac = "00:1E:2F:3A:4B:5C",
            name = "Security Camera",
            description = "Outdoor camera",
            manufacturer = "TP-Link",
            userId = currentUserId
        ),
        Device(
            ip = "192.168.1.105",
            mac = "00:1E:2F:3A:4B:5C",
            name = "Security Camera",
            description = "Outdoor camera",
            manufacturer = "TP-Link",
            userId = currentUserId
        ),
        Device(
            ip = "192.168.1.105",
            mac = "00:1E:2F:3A:4B:5C",
            name = "Security Camera",
            description = "Outdoor camera",
            manufacturer = "TP-Link",
            userId = currentUserId
        )
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityScanDevicesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeToolbar()
        setupRecyclerView()
        startScanningDevices()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun startScanningDevices() {
        binding.btnStartScan.setOnClickListener {
            //loadDevices()
            loadDevicesWithSavedData()
        }
    }

    private fun loadDevicesWithSavedData() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            loadDevices()
            return
        }

        firestore.collection("saved_devices")
            .document(currentUser.uid)
            .collection("devices")
            .get()
            .addOnSuccessListener { documents ->
                val savedDevices = documents.map { it.toObject(Device::class.java) }

                // Mescla a lista fake com os dados salvos
                val mergedDevices = fakeDevices.map { fakeDevice ->
                    savedDevices.find { it.mac == fakeDevice.mac } ?: fakeDevice
                }

                scanDevicesAdapter.addList(mergedDevices)
            }
            .addOnFailureListener {
                loadDevices()
            }
    }

    private fun setupRecyclerView() {
        scanDevicesAdapter = ScanDevicesAdapter { device ->
            showEditDialog(device)
        }
        
        binding.rvListScanDevices.apply {
            adapter = scanDevicesAdapter
            layoutManager = LinearLayoutManager(this@ScanDevicesActivity)
            setHasFixedSize(true)

            // Configurações adicionais para a barra de rolagem
            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
        }
    }

    private fun showEditDialog(device: Device) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_device, null)

        dialogView.apply {
            findViewById<TextInputEditText>(R.id.editName).setText(device.name)
            findViewById<TextInputEditText>(R.id.editDescription).setText(device.description)

            // Esconde o botão de foto por enquanto
            findViewById<Button>(R.id.button).visibility = View.GONE
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Save Device")
            .setPositiveButton("Save") { _, _ ->
                val editedDevice = device.copy(
                    name = dialogView.findViewById<TextInputEditText>(R.id.editName).text.toString(),
                    description = dialogView.findViewById<TextInputEditText>(R.id.editDescription).text.toString()
                )
                saveDeviceToUserCollection(editedDevice)
                loadDevicesWithSavedData()
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun saveDeviceToUserCollection(device: Device) {
        val user = firebaseAuth.currentUser
        if (user == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        // Adiciona o userId ao dispositivo
        val deviceWithUser = device.copy(userId = user.uid)

        firestore.collection("saved_devices")
            .document(user.uid)
            .collection("devices")
            .document(device.mac) // Usa o MAC como ID do documento
            .set(deviceWithUser)
            .addOnSuccessListener {
                Toast.makeText(this, "Device saved successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving device: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadDevices() {
        scanDevicesAdapter.addList(fakeDevices)
    }

    private fun initializeToolbar() {
        val toolbar = binding.includeTbScanDevices.tbMain
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "Scan Devices"
            setDisplayHomeAsUpEnabled(true)
        }

        binding.btnLogoutScanDevices.setOnClickListener {
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