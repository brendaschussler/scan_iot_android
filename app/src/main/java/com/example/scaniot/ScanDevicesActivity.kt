package com.example.scaniot

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.scaniot.databinding.ActivityScanDevicesBinding
import com.example.scaniot.helper.Permissions
import com.example.scaniot.model.Device
import com.example.scaniot.model.NetworkScanner
import com.example.scaniot.model.ScanDevicesAdapter
import com.example.scaniot.utils.RootUtils
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

class ScanDevicesActivity : AppCompatActivity() {

    private lateinit var scanDevicesAdapter: ScanDevicesAdapter
    private lateinit var binding: ActivityScanDevicesBinding

    private lateinit var openGalleryLauncher: ActivityResultLauncher<String>
    private lateinit var openCameraLauncher: ActivityResultLauncher<Intent>

    private var uriSelectedImage: Uri? = null
    private var bitmapSelectedImage: Bitmap? = null

    private var currentDialogView: View? = null

    private var scannedNotSavedDevices = mutableListOf<Device>()

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

    private val scannedDevices = mutableListOf<Device>()
    private lateinit var networkScanner: NetworkScanner

    private val valPermissions = listOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.READ_MEDIA_IMAGES,
        android.Manifest.permission.ACCESS_WIFI_STATE,
        android.Manifest.permission.ACCESS_NETWORK_STATE,
        android.Manifest.permission.CHANGE_WIFI_STATE,
        android.Manifest.permission.INTERNET
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityScanDevicesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Permissions.myRequestPermissions(
            this, valPermissions
        )

        initializeToolbar()
        setupRecyclerView()
        initializeClickEvents()

        openGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                currentDialogView?.findViewById<ImageView>(R.id.selectedImageDevice)?.setImageURI(uri)
                uriSelectedImage = uri
            } else {
                Toast.makeText(this, "No image selected", Toast.LENGTH_LONG).show()
            }
        }

        openCameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.data?.extras?.getParcelable("data", Bitmap::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    result.data?.extras?.getParcelable("data")
                }

                bitmap?.let {
                    bitmapSelectedImage = it
                    uriSelectedImage = null
                    currentDialogView?.findViewById<ImageView>(R.id.selectedImageDevice)?.setImageBitmap(it)
                }
            }
        }

        networkScanner = NetworkScanner(this)
        binding.progressBarCircular.visibility = View.GONE


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initializeClickEvents() {
        binding.btnStartScan.setOnClickListener {

            binding.btnStartScan.isEnabled = false
            binding.progressBarCircular.visibility = View.VISIBLE
            binding.rvListScanDevices.visibility = View.GONE

            RootUtils.checkRootAccess(this) { hasRoot ->
                if (hasRoot) {
                    startScanningDevices()
                } else {
                    binding.btnStartScan.isEnabled = true
                    binding.progressBarCircular.visibility = View.GONE
                    binding.rvListScanDevices.visibility = View.GONE
                }
            }
        }
    }

    private fun startScanningDevices() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val discoveredDevices = withContext(Dispatchers.IO) {
                    networkScanner.getConnectedHotspotDevices()
                }

                scannedDevices.clear()
                scannedDevices.addAll(discoveredDevices)

                if (scannedDevices.isEmpty()) {
                    Toast.makeText(
                        this@ScanDevicesActivity,
                        "Nenhum dispositivo encontrado na rede",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                loadDevicesWithSavedData()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ScanDevicesActivity,
                    "Erro no scan: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                Log.e("NetworkScan", "Erro ao escanear rede", e)
            } finally {
                binding.rvListScanDevices.visibility = View.VISIBLE
                binding.btnStartScan.isEnabled = true
                binding.progressBarCircular.visibility = View.GONE
            }
        }
    }

    private fun loadDevicesWithSavedData() {
        val currentUser = firebaseAuth.currentUser ?: run {
            loadDevices()
            return
        }

        firestore.collection("saved_devices")
            .document(currentUser.uid)
            .collection("devices")
            .get()
            .addOnSuccessListener { savedDocuments ->
                val savedDevices = savedDocuments.map { it.toObject(Device::class.java) }

                firestore.collection("scanned_not_saved_devices")
                    .document(currentUser.uid)
                    .collection("devices")
                    .get()
                    .addOnSuccessListener { notSavedDocuments ->
                        val existingNotSavedDevices = notSavedDocuments.map { it.toObject(Device::class.java) }

                        // Identificar novos dispositivos escaneados que não estão salvos
                        val newDevices = scannedDevices.filter { scannedDevice ->
                            !savedDevices.any { it.mac == scannedDevice.mac } &&
                                    !existingNotSavedDevices.any { it.mac == scannedDevice.mac }
                        }

                        // Adicionar novos dispositivos à coleção de não salvos
                        if (newDevices.isNotEmpty()) {
                            saveNewDevicesToNotSaved(newDevices)
                        }

                        // Lista para armazenar dispositivos que precisam atualizar o IP no Firebase
                        val devicesToUpdate = mutableListOf<Device>()

                        // Criar lista final combinando informações
                        val allDevices = scannedDevices.map { scannedDevice ->
                            when {
                                // Dispositivo salvo - mantém todas as informações salvas, mas atualiza o IP
                                savedDevices.any { it.mac == scannedDevice.mac } -> {
                                    val savedDevice = savedDevices.first { it.mac == scannedDevice.mac }
                                    if (savedDevice.ip != scannedDevice.ip) {
                                        // Se o IP mudou, adiciona à lista de atualização
                                        devicesToUpdate.add(savedDevice.copy(ip = scannedDevice.ip))
                                    }
                                    savedDevice.copy(
                                        ip = scannedDevice.ip, // Atualiza apenas o IP
                                        isNew = false
                                    )
                                }
                                // Dispositivo não salvo mas já escaneado antes
                                existingNotSavedDevices.any { it.mac == scannedDevice.mac } -> {
                                    val notSavedDevice = existingNotSavedDevices.first { it.mac == scannedDevice.mac }
                                    notSavedDevice.copy(
                                        ip = scannedDevice.ip // Atualiza apenas o IP
                                    )
                                }
                                // Novo dispositivo nunca visto antes
                                else -> scannedDevice.copy(isNew = true)
                            }
                        }

                        // Atualizar IPs no Firebase se necessário
                        if (devicesToUpdate.isNotEmpty()) {
                            updateIpsInFirebase(devicesToUpdate)
                        }

                        scanDevicesAdapter = ScanDevicesAdapter(
                            onEditClick = { device -> showEditDialog(device) },
                            savedDevices = savedDevices,
                            scannedNotSavedDevices = existingNotSavedDevices
                        )
                        binding.rvListScanDevices.adapter = scanDevicesAdapter
                        scanDevicesAdapter.addList(allDevices)
                    }
            }
            .addOnFailureListener { loadDevices() }
    }

    private fun updateIpsInFirebase(devices: List<Device>) {
        val currentUser = firebaseAuth.currentUser ?: return
        val batch = firestore.batch()

        devices.forEach { device ->
            val docRef = firestore.collection("saved_devices")
                .document(currentUser.uid)
                .collection("devices")
                .document(device.mac)

            batch.update(docRef, "ip", device.ip)
        }

        batch.commit()
            .addOnSuccessListener {
                Log.d("ScanDevices", "IPs atualizados com sucesso para ${devices.size} dispositivos")
            }
            .addOnFailureListener { e ->
                Log.e("ScanDevices", "Erro ao atualizar IPs: ${e.message}")
            }
    }

    private fun saveNewDevicesToNotSaved(newDevices: List<Device>) {
        val user = firebaseAuth.currentUser ?: return
        val batch = firestore.batch()

        newDevices.forEach { device ->
            val docRef = firestore.collection("scanned_not_saved_devices")
                .document(user.uid)
                .collection("devices")
                .document(device.mac)
            batch.set(docRef, device.copy(userId = user.uid))
        }

        batch.commit().addOnCompleteListener {
             // list will be updated on next loading
        }
    }


    private fun setupRecyclerView() {
        scanDevicesAdapter = ScanDevicesAdapter (
            onEditClick = { device ->
                showEditDialog(device)
            },
            savedDevices = emptyList(), // Será atualizado em loadDevicesWithSavedData
            scannedNotSavedDevices = emptyList() // Será atualizado em loadDevicesWithSavedData
        )

        binding.rvListScanDevices.apply {
            adapter = scanDevicesAdapter
            layoutManager = LinearLayoutManager(this@ScanDevicesActivity)
            setHasFixedSize(true)

            // config to scroll bar
            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
        }
    }

    private fun showEditDialog(device: Device) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_device, null)
        currentDialogView = dialogView

        // Configurar o AutoCompleteTextView para as categorias
        val categories = arrayOf(
            "Camera",
            "Game Console",
            "Hub/Gateway",
            "Laptop",
            "Medical Device",
            "Network Storage (NAS)",
            "Printer",
            "Router/Access Point",
            "Sensor",
            "Smart Appliance",
            "Smart Doorbell",
            "Smart Light",
            "Smart Lock",
            "Smart Plug",
            "Smart Speaker",
            "Smart Thermostat",
            "Smart TV",
            "Smartwatch",
            "Smartphone",
            "Sound System",
            "Tablet",
            "Vehicle",
            "Voice Assistants",
            "Wearable",
            "Other"
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        val categoryInput = dialogView.findViewById<AutoCompleteTextView>(R.id.editCategory)
        categoryInput.setAdapter(adapter)

        // Definir a categoria atual do dispositivo, se existir
        device.deviceCategory?.let {
            categoryInput.setText(it, false)
        }

        dialogView.apply {
            findViewById<TextInputEditText>(R.id.editName).setText(device.name)
            findViewById<TextInputEditText>(R.id.editDescription).setText(device.description)
            findViewById<TextInputEditText>(R.id.editVendor).setText(device.vendor)
            findViewById<TextInputEditText>(R.id.editModel).setText(device.deviceModel)
            findViewById<TextInputEditText>(R.id.editLocation).setText(device.deviceLocation)
            findViewById<TextInputEditText>(R.id.editVersion).setText(device.deviceVersion)
            findViewById<TextInputEditText>(R.id.editType).setText(device.deviceType)
        }

        dialogView.findViewById<Button>(R.id.btnLoadImageGallery).setOnClickListener {
            openGalleryLauncher.launch("image/*")
        }

        dialogView.findViewById<Button>(R.id.btnOpenCamera).setOnClickListener {
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val activities = packageManager.queryIntentActivities(cameraIntent, PackageManager.MATCH_ALL)

            if (activities.isNotEmpty()) {
                openCameraLauncher.launch(cameraIntent)
            } else {
                Toast.makeText(this, "No camera app available", Toast.LENGTH_LONG).show()
            }
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Save Device")
            .setPositiveButton("Save") { _, _ ->
                val editedDevice = device.copy(
                    name = dialogView.findViewById<TextInputEditText>(R.id.editName).text.toString(),
                    description = dialogView.findViewById<TextInputEditText>(R.id.editDescription).text.toString(),
                    vendor = dialogView.findViewById<TextInputEditText>(R.id.editVendor).text.toString(),
                    deviceModel = dialogView.findViewById<TextInputEditText>(R.id.editModel).text.toString(),
                    deviceLocation = dialogView.findViewById<TextInputEditText>(R.id.editLocation).text.toString(),
                    deviceVersion = dialogView.findViewById<TextInputEditText>(R.id.editVersion).text.toString(),
                    deviceType = dialogView.findViewById<TextInputEditText>(R.id.editType).text.toString(),
                    deviceCategory = dialogView.findViewById<AutoCompleteTextView>(R.id.editCategory).text.toString()
                )
                saveDeviceToUserCollection(editedDevice)
                loadDevicesWithSavedData()
                uploadGallery(
                    device
                )
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }


    private fun uploadGallery(device: Device) {

        val imgName = UUID.randomUUID().toString()
        val thisMac = device.mac

        val outputStream = ByteArrayOutputStream()
        bitmapSelectedImage?.compress(
            Bitmap.CompressFormat.JPEG,
            70,
            outputStream
        )

        if(uriSelectedImage != null && currentUserId != null) {
            storage
                .getReference("images")
                .child(currentUserId)
                .child(imgName)
                .putFile(uriSelectedImage!!) //!! for certain not null
                .addOnSuccessListener { task ->
                    task.metadata?.reference?.downloadUrl
                        ?.addOnSuccessListener { urlFirebase ->

                            val dados = mapOf(
                                "photoUrl" to urlFirebase.toString()
                            )
                            val updatedDeviceGlr = device.copy(photoUrl = urlFirebase.toString())

                            updateDeviceData(thisMac, dados)
                            //scanDevicesAdapter.updateDevice(updatedDeviceGlr)
                            loadDevicesWithSavedData()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error uploading image", Toast.LENGTH_LONG).show()
                }
        } else if(bitmapSelectedImage != null){
            storage
                .getReference("images")
                .child(currentUserId)
                .child(imgName)
                .putBytes( outputStream.toByteArray() )
                .addOnSuccessListener { task ->
                    task.metadata?.reference?.downloadUrl
                        ?.addOnSuccessListener { urlFirebase ->

                            val dados = mapOf(
                                "photoUrl" to urlFirebase.toString()
                            )
                            val updatedDeviceCam = device.copy(photoUrl = urlFirebase.toString())

                            updateDeviceData(thisMac, dados)
                            //scanDevicesAdapter.updateDevice(updatedDeviceCam)
                            loadDevicesWithSavedData()

                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error uploading image", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun updateDeviceData(thisMac: String?, dados: Map<String, String>) {

        val thisMacAdress = thisMac
        if (thisMacAdress != null){
            firestore
                .collection("saved_devices")
                .document(currentUserId)
                .collection("devices")
                .document(thisMacAdress)
                .update(dados)
        }
    }

    private fun saveDeviceToUserCollection(device: Device) {
        val user = firebaseAuth.currentUser ?: return

        val deviceWithUser = device.copy(userId = user.uid, isNew = false)

        // Save in firestore
        firestore.collection("saved_devices")
            .document(user.uid)
            .collection("devices")
            .document(device.mac)
            .set(deviceWithUser)
            .addOnSuccessListener {
                // Remove from not saved list if necessary
                firestore.collection("scanned_not_saved_devices")
                    .document(user.uid)
                    .collection("devices")
                    .document(device.mac)
                    .delete()

                scannedNotSavedDevices.removeAll { it.mac == device.mac }
                Toast.makeText(this, "Device saved successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving device: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadDevices() {
        scanDevicesAdapter.addList(scannedDevices)
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