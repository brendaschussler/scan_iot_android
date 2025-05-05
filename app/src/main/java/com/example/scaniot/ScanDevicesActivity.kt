package com.example.scaniot

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
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
import com.example.scaniot.model.RootManager
import com.example.scaniot.model.ScanDevicesAdapter
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

    private lateinit var rootManager: RootManager
    private var rootAccessGranted = false

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

    /*val scannedDevices = listOf(
        Device(
            ip = "192.168.1.111",
            mac = "55:1A:2B:3C:4D:95",
            name = "Smart TV",
            description = "Samsung 4K UHD Smart TV",
            vendor = "Samsung",
            deviceModel = "QN65Q80AAFXZA",
            deviceLocation = "Living Room",
            deviceVersion = "T-KT2DEUC-2305.5",
            deviceType = "television",
            userId = currentUserId
        ),
        Device(
            ip = "192.168.1.102",
            mac = "00:1B:2C:3D:4E:5F",
            name = "My Smartphone",
            description = "Personal Android Phone",
            vendor = "Xiaomi",
            deviceModel = "Redmi Note 10 Pro",
            deviceLocation = "Bedroom",
            deviceVersion = "Android 13",
            deviceType = "smartphone",
            userId = currentUserId
        ),
        Device(
            ip = "192.168.1.103",
            mac = "00:1C:2D:3E:4F:5A",
            name = "Work Laptop",
            description = "Company issued Macbook",
            vendor = "Apple",
            deviceModel = "MacBook Air M2",
            deviceLocation = "Home Office",
            deviceVersion = "macOS 14.0",
            deviceType = "laptop",
            userId = currentUserId
        ),
        Device(
            ip = "192.168.1.104",
            mac = "00:1D:2E:3F:4A:5B",
            name = "Bedroom Light",
            description = "RGB Smart Bulb",
            vendor = "Philips",
            deviceModel = "Hue White and Color",
            deviceLocation = "Master Bedroom",
            deviceVersion = "1.93.3",
            deviceType = "light",
            userId = currentUserId
        ),
        Device(
            ip = "192.168.1.105",
            mac = "00:1E:2F:3A:4B:5C",
            name = "Front Door Camera",
            description = "Outdoor Security Camera",
            vendor = "TP-Link",
            deviceModel = "Tapo C310",
            deviceLocation = "Front Entrance",
            deviceVersion = "1.1.9 Build 20230905",
            deviceType = "camera",
            userId = currentUserId
        )
    )*/

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
        startScanningDevices()

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
                    uriSelectedImage = null // Limpa a URI se houver
                    currentDialogView?.findViewById<ImageView>(R.id.selectedImageDevice)?.setImageBitmap(it)
                }
            }
        }

        networkScanner = NetworkScanner(this)
        binding.progressBarCircular.visibility = View.GONE

        rootManager = RootManager(this)
        //checkRootStatus()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun checkRootStatus() {
        if (!rootManager.isDeviceRooted()) {
            showDeviceNotRootedWarning()
            return
        }

        if (rootManager.isDeviceRooted()){
            rootManager.installNetworkToolsIfNeeded()
        }

        CoroutineScope(Dispatchers.Main).launch {
            if (rootManager.wasRootDenied()) {
                showRootDeniedMessage()
            } else {
                verifyRootAccess()
            }
        }
    }

    private suspend fun verifyRootAccess() {
        binding.progressBarCircular.visibility = View.GONE

        try {
            rootAccessGranted = rootManager.hasRootAccess()

            if (!rootAccessGranted) {
                requestRootPermission()
            } else {
                Toast.makeText(this@ScanDevicesActivity,
                    "Permissão root já concedida", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {

        } finally {
            binding.progressBarCircular.visibility = View.GONE
        }
    }

    private fun requestRootPermission() {
        AlertDialog.Builder(this)
            .setTitle("Permissão Root Necessária")
            .setMessage("Para obter endereços MAC completos")
            .setPositiveButton("Conceder") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    grantRootPermission()
                }
            }
            .setNegativeButton("Recusar") { _, _ ->
                rootManager.setRootDenied(true)
                showRootDeniedMessage()
            }
            .setCancelable(false)
            .show()
    }

    private suspend fun grantRootPermission() {
        binding.progressBarCircular.visibility = View.VISIBLE
        try {
            val success = rootManager.executeRootCommand("echo 'Teste'") != null
            rootAccessGranted = success

            if (success) {
                rootManager.setRootDenied(false)
                Toast.makeText(this@ScanDevicesActivity,
                    "Permissão concedida com sucesso", Toast.LENGTH_SHORT).show()
            } else {
                rootManager.setRootDenied(true)
                showRootDeniedMessage()
            }
        } catch (e: Exception) {

        } finally {
            binding.progressBarCircular.visibility = View.GONE
        }
    }

    private fun showRootDeniedMessage() {
        AlertDialog.Builder(this)
            .setTitle("Funcionalidade Limitada")
            .setMessage("Sem permissão root, alguns recursos como obtenção de MAC addresses serão limitados.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showDeviceNotRootedWarning() {
        AlertDialog.Builder(this)
            .setTitle("Dispositivo não rootado")
            .setMessage("Para obter endereços MAC completos, o dispositivo precisa ter acesso root.")
            .setPositiveButton("OK", null)
            .show()
    }


    /*private fun startScanningDevices() {
        binding.btnStartScan.setOnClickListener {
            //loadDevices()
            loadDevicesWithSavedData()
        }
    }*/

    private fun startScanningDevices() {
        binding.btnStartScan.setOnClickListener {

            //checkRootStatus()

            binding.btnStartScan.isEnabled = false
            binding.progressBarCircular.visibility = View.VISIBLE

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val discoveredDevices = withContext(Dispatchers.IO) {
                        networkScanner.scanNetworkDevices(hasRootAccess = rootAccessGranted)
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
                    binding.btnStartScan.isEnabled = true
                    binding.progressBarCircular.visibility = View.GONE
                }
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
            if (cameraIntent.resolveActivity(packageManager) != null) {
                openCameraLauncher.launch(cameraIntent)
            } else {
                Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show()
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
                    deviceType = dialogView.findViewById<TextInputEditText>(R.id.editType).text.toString()
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