package com.example.scaniot

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.SearchView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.scaniot.databinding.ActivityScanDevicesBinding
import com.example.scaniot.databinding.DialogEditDeviceBinding
import com.example.scaniot.model.Device
import com.example.scaniot.model.DeviceAdapter
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ScanDevicesActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityScanDevicesBinding.inflate(layoutInflater)
    }

    private val viewModel: ScanDevicesViewModel by viewModels()
    private lateinit var adapter: DeviceAdapter
    private val firebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        initializeToolbar()
        setupRecyclerView()
        setupObservers()
        setupClickListeners()

        viewModel.scanNetwork()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupRecyclerView() {
        adapter = DeviceAdapter(
            onEditClick = { device -> showEditDialog(device) },
            onSaveClick = { device -> viewModel.saveDevice(device) }
        )

        binding.devicesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ScanDevicesActivity)
            adapter = this@ScanDevicesActivity.adapter
            setHasFixedSize(true)
        }
    }

    private fun showEditDialog(device: Device) {
        val dialogBinding = DialogEditDeviceBinding.inflate(layoutInflater)

        dialogBinding.editName.setText(device.name)
        dialogBinding.editDescription.setText(device.description)

        Glide.with(this)
            .load(device.photoUrl ?: R.drawable.ic_device_unknown)
            .into(dialogBinding.devicePhoto)

        AlertDialog.Builder(this)
            .setTitle("Editar Dispositivo")
            .setView(dialogBinding.root)
            .setPositiveButton("Salvar") { _, _ ->
                val updatedDevice = device.copy(
                    name = dialogBinding.editName.text.toString(),
                    description = dialogBinding.editDescription.text.toString()
                )
                viewModel.saveDevice(updatedDevice)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setupObservers() {
        // Para dispositivos
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.devices.collect { devices ->
                    adapter.submitList(devices)
                }
            }
        }

        // Para loading state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLoading.collect { isLoading ->
                    binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.filterDevices(newText.orEmpty())
                return true
            }
        })

        binding.btnLogoutScanDevices.setOnClickListener {
            logoutUser()
        }
    }

    private fun initializeToolbar() {
        val toolbar = binding.includeTbScanDevices.tbMain
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "ScanIoT"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun logoutUser() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Tem certeza que deseja sair?")
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("Sair") { _, _ ->
                firebaseAuth.signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}