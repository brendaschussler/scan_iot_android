package com.example.scaniot

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.scaniot.databinding.ActivityDashboardScreenBinding
import com.example.scaniot.view.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DashboardScreenActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityDashboardScreenBinding.inflate( layoutInflater )
    }

    private val firebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    private val firestore by lazy {
        FirebaseFirestore.getInstance()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView( binding.root )

        initializeToolbar()
        initializeClickEvents()
        fetchUserNameFromFirestore()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun fetchUserNameFromFirestore() {
        val userId = firebaseAuth.currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                val name = document.getString("name") ?: "User"
                binding.textNameUser.text = name
            }
            .addOnFailureListener {
                binding.textNameUser.text = "User"
            }
    }

    private fun navigateToScan(){
        binding.btnScanDevices.setOnClickListener {
            startActivity(
                Intent(this, ScanDevicesActivity::class.java)
            )
        }
    }

    private fun navigateToSavedDevices(){
        binding.btnSavedDevices.setOnClickListener {
            startActivity(
                Intent(this, SavedDevicesActivity::class.java)
            )
        }
    }

    private fun navigateToCapturedPackets() {
        binding.btnCapturedPackets.setOnClickListener {
            // Força recarregar do Firestore com estado atualizado
            val intent = Intent(this, CapturedPacketsActivity::class.java)
            intent.putExtra("force_refresh", true)  // Flag para atualizar
            startActivity(intent)
        }
    }

    private fun initializeClickEvents() {
        navigateToScan()
        navigateToSavedDevices()
        navigateToCapturedPackets()
    }

    private fun initializeToolbar() {
        val toolbar = binding.includeToolbarMain.tbMain
        setSupportActionBar( toolbar )
        supportActionBar?.apply {
            title = "ScanIoT"
        }

        binding.btnLogout.setOnClickListener {
            logoutUser()
        }

    }

    private fun logoutUser() {

        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to log out")
            .setNegativeButton("Cancel"){dialog, position -> }
            .setPositiveButton("Yes"){dialog, position ->
                firebaseAuth.signOut()
                startActivity(
                    Intent(applicationContext, LoginActivity::class.java)
                )
            }
            .create()
            .show()
    }

}