package com.example.scaniot

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.scaniot.databinding.ActivityLoginBinding
import com.example.scaniot.utils.showMessage
import com.example.scaniot.viewModel.LoginViewModel
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityLoginBinding.inflate(layoutInflater)
    }

    private val viewModel: LoginViewModel by viewModels()

    private val firebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(binding.root)
        initializeClickEvents()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.verifyUserIsLoggedIn(
            onVerified = {
                startActivity(Intent(this, DashboardScreenActivity::class.java))
            },
            onNotVerified = {
                showEmailRequiredAlert()
            }
        )
    }

    private fun showEmailRequiredAlert() {
        AlertDialog.Builder(this)
            .setTitle("Please verify your email")
            .setMessage("We sent you an email to ${firebaseAuth.currentUser?.email} " +
                    "to verify your email adress and activate your account.")
            .setPositiveButton("Close") { _, _ ->
                FirebaseAuth.getInstance().signOut()
            }
            .show()
    }

    private fun showVerificationAlert() {
        AlertDialog.Builder(this)
            .setTitle("Verify your email adress")
            .setMessage("We sent you an email to ${firebaseAuth.currentUser?.email}. " +
                    "Click on the link in that email to verify your account.")
            .setNegativeButton("Close") { _, _ ->
                FirebaseAuth.getInstance().signOut()
            }
            .show()
    }

    private fun initializeClickEvents() {
        binding.textSignUp.setOnClickListener {
            startActivity(
                Intent(this, RegisterActivity::class.java)
            )
        }
        binding.btnLogin.setOnClickListener {
            viewModel.email = binding.editLoginEmail.text.toString()
            viewModel.password = binding.editLoginPassword.text.toString()

            if (validateFields()) {
                viewModel.loginUser(
                    onSuccessVerified = {
                        startActivity(Intent(this, DashboardScreenActivity::class.java))
                    },
                    onSuccessNotVerified = {
                        showVerificationAlert()
                    },
                    onInvalidEmail = {
                        showMessage("Invalid e-mail.")
                    },
                    onInvalidCredentials = {
                        showMessage("Invalid e-mail or password.")
                    },
                    onFailure = { e ->
                        showMessage("Error: ${e.message}")
                    }
                )
            }
        }
        binding.txtForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    private fun showForgotPasswordDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null)
        val editEmail = dialogView.findViewById<EditText>(R.id.editEmailForgotPassword)

        AlertDialog.Builder(this)
            .setTitle("Reset Password")
            .setView(dialogView)
            .setPositiveButton("Send Link") { _, _ ->
                val email = editEmail.text.toString().trim()
                if (email.isNotEmpty()) {
                    viewModel.sendPasswordResetEmail(
                        email = email,
                        onSuccess = { message -> showMessage(message) },
                        onFailure = { message -> showMessage(message) },
                        onInvalidUser = { showMessage("Email not registered") }
                    )
                } else {
                    showMessage("Please enter your email")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun validateFields(): Boolean {
        viewModel.email = binding.editLoginEmail.text.toString()
        viewModel.password = binding.editLoginPassword.text.toString()

        if (viewModel.email.isNotEmpty()) {
            binding.textInputLayoutLoginEmail.error = null
            if (viewModel.password.isNotEmpty()) {
                binding.textInputLayoutLoginPassword.error = null
                return true
            } else {
                binding.textInputLayoutLoginPassword.error = "Enter your password"
                return false
            }
        } else {
            binding.textInputLayoutLoginEmail.error = "Enter your e-mail"
            return false
        }
    }
}