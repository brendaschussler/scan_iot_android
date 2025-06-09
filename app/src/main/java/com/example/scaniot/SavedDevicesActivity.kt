package com.example.scaniot

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.scaniot.databinding.ActivitySavedDevicesBinding
import com.example.scaniot.model.CaptureRepository
import com.example.scaniot.model.Device
import com.example.scaniot.model.SavedDevicesAdapter
import com.example.scaniot.utils.showMessage
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.example.scaniot.utils.RootUtils
import com.example.scaniot.utils.TcpdumpUtils
import android.widget.SearchView

class SavedDevicesActivity : AppCompatActivity() {

    private val PERMISSIONS_REQUEST_CODE = 100

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

    private var allDevices = emptyList<Device>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySavedDevicesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeToolbar()
        setupRecyclerView()
        loadSavedDevices()
        initializeClickEvents()
        setupSearchView()

        if (!hasStoragePermissions()) {
            requestStoragePermissions()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets

        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterDevices(newText.orEmpty())
                return true
            }
        })
    }

    private fun filterDevices(query: String) {
        val filteredList = if (query.isEmpty()) {
            allDevices
        } else {
            allDevices.filter { device ->
                device.name?.contains(query, ignoreCase = true) == true ||
                        device.mac?.contains(query, ignoreCase = true) == true ||
                        device.ip?.contains(query, ignoreCase = true) == true ||
                        device.vendor?.contains(query, ignoreCase = true) == true
            }
        }
        savedDevicesAdapter.submitList(filteredList)
    }

    private fun hasStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, PERMISSIONS_REQUEST_CODE)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                PERMISSIONS_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d("Permissions", "Permissions Granted")
            } else {
                Toast.makeText(
                    this,
                    "Permissions are required to save the capture files.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Android 11+ (API 30+)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PERMISSIONS_REQUEST_CODE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Log.d("Permissions", "Full access granted")
            }
        }
    }

    private fun initializeClickEvents() {
        binding.btnStartCapture.setOnClickListener {
            val selectedDevices = savedDevicesAdapter.getSelectedDevices()

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

        val editHours = dialogView.findViewById<TextInputEditText>(R.id.editHours)
        val editMinutes = dialogView.findViewById<TextInputEditText>(R.id.editMinutes)
        val editSeconds = dialogView.findViewById<TextInputEditText>(R.id.editSeconds)

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
                        showMessage("Please set a number of packages greater than zero")
                        return@setPositiveButton
                    }

                    if (packetCount > 300_000) {
                        showMessage("Maximum number of packages exceeded (300k)")
                        return@setPositiveButton
                    }

                    checkRootAndStartCapture(selectedDevices, packetCount, 0, filename)

                } else {

                    val hours = editHours.text.toString().toIntOrNull() ?: 0
                    val minutes = editMinutes.text.toString().toIntOrNull() ?: 0
                    val seconds = editSeconds.text.toString().toIntOrNull() ?: 0


                    if (hours == 0 && minutes == 0 && seconds == 0) {
                        showMessage("Please set at least some time for capture")
                        return@setPositiveButton
                    }

                    val totalSeconds = (hours * 3600) + (minutes * 60) + seconds
                    if (totalSeconds > 43_200) {
                        Toast.makeText(this, "Total time exceeds maximum limit (12 hours)", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    val milliseconds = totalSeconds * 1000L

                    checkRootAndStartCaptureByTime(selectedDevices, 0, milliseconds, filename)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkRootAndStartCapture(
        devices: List<Device>,
        packetCount: Int,
        timeLimitMs: Long,
        filename: String
    ) {
        RootUtils.checkRootAccess(this) { hasRoot ->
            if (hasRoot) {
                TcpdumpUtils.checkTcpdumpAvailable(this) { isInstalled ->
                    if (isInstalled) {
                        startCapturedPacketsActivity(devices, packetCount, timeLimitMs, filename)
                    } else {
                        showTcpdumpMessage()
                    }
                }
            }
        }
    }

    private fun checkRootAndStartCaptureByTime(
        devices: List<Device>,
        packetCount: Int,
        timeLimitMs: Long,
        filename: String
    ) {
        RootUtils.checkRootAccess(this) { hasRoot ->
            if (hasRoot) {
                TcpdumpUtils.checkTcpdumpAvailable(this) { isAvailable ->
                    if (isAvailable) {
                        TcpdumpUtils.checkTimeoutInstalled(this) { isInstalled ->
                            if(isInstalled){
                                startCapturedPacketsActivity(devices, packetCount, timeLimitMs, filename)
                            } else {
                                showTimeoutMessage()
                            }
                        }
                    } else {
                        showTcpdumpMessage()
                    }
                }
            }
        }
    }

    private fun showTimeoutMessage() {
        AlertDialog.Builder(this)
            .setTitle("Timeout is not installed\n")
            .setMessage("Timeout is required for packet capture by time limit. Please install it by following the tutorial in help icon")
            .setPositiveButton("Ok") { _, _ ->
            }
            .show()
    }

    private fun showTcpdumpMessage() {
        AlertDialog.Builder(this)
            .setTitle("Tcpdump is not installed\n")
            .setMessage("Tcpdump is required for packet capture by time limit. Please install it by following the tutorial in help icon")
            .setPositiveButton("Ok") { _, _ ->
            }
            .show()
    }

    private fun startCapturedPacketsActivity(devices: List<Device>, packetCount: Int, timeLimitMs: Long, filename: String) {
        val devicesWithCapture = devices.map {
            it.copy(
                capturing = true,
                captureTotal = if (timeLimitMs > 0) 0 else packetCount.coerceAtLeast(1),
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
                allDevices = documents.map { doc ->
                    doc.toObject(Device::class.java).copy(mac = doc.id)
                }.sortedBy { it.name?.lowercase() }

                savedDevicesAdapter.submitList(allDevices)
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
                    .document(device.mac)
                    .delete()
                    .addOnSuccessListener {
                        loadSavedDevices()
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