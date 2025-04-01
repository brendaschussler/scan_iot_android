package com.example.scaniot

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.scaniot.databinding.ActivityDashboardScreenBinding
import com.google.firebase.auth.FirebaseAuth

class DashboardScreenActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityDashboardScreenBinding.inflate( layoutInflater )
    }

    private val firebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView( binding.root )

        initializeToolbar()
        initializeClickEvents()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun navigateToScan(){
        binding.btnScanDevices.setOnClickListener {
            startActivity(
                Intent(this, ScanDevicesActivity::class.java)
            )
        }
    }

    private fun initializeClickEvents() {
        navigateToScan()
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

/*        addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.menu_main, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    when(menuItem.itemId){
                        R.id.logout_item -> {
                            logoutUser()
                        }
                    }
                    return true
                }

            }
        )*/
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