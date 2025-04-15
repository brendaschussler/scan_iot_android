package com.example.scaniot.view

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
import androidx.lifecycle.lifecycleScope
import com.example.scaniot.DashboardScreenActivity
import com.example.scaniot.R
import com.example.scaniot.RegisterActivity
import com.example.scaniot.databinding.ActivityLoginBinding
import com.example.scaniot.utils.showMessage
import com.example.scaniot.viewModel.LoginState
import com.example.scaniot.viewModel.LoginViewModel
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityLoginBinding.inflate(layoutInflater)
    }

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        setupWindowInsets()
        setupClickListeners()
        observeLoginState()
    }

    override fun onStart() {
        super.onStart()
        if (viewModel.checkUserLoggedIn()) {
            navigateToDashboard()
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupClickListeners() {
        binding.textSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.btnLogin.setOnClickListener {
            if (validateFields()) {
                val email = binding.editLoginEmail.text.toString()
                val password = binding.editLoginPassword.text.toString()
                viewModel.loginUser(email, password)
            }
        }

        binding.txtForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    private fun observeLoginState() {
        lifecycleScope.launchWhenStarted {
            viewModel.loginState.collect { state ->
                when (state) {
                    is LoginState.Loading -> {
                        // Mostrar progress bar ou loading
                    }
                    is LoginState.Success -> {
                        navigateToDashboard()
                    }
                    is LoginState.EmailNotVerified -> {
                        showVerificationAlert()
                    }
                    is LoginState.Error -> {
                        showMessage(state.message)
                    }
                    LoginState.Idle -> {
                        // Estado inicial, não faz nada
                    }
                }
            }
        }
    }

    private fun navigateToDashboard() {
        startActivity(Intent(this, DashboardScreenActivity::class.java))
        finish()
    }

    private fun showVerificationAlert() {
        AlertDialog.Builder(this)
            .setTitle("Verify your email address")
            .setMessage("We sent you an email to ${FirebaseAuth.getInstance().currentUser?.email}. " +
                    "Click on the link in that email to verify your account.")
            .setPositiveButton("Resend Verification Email") { _, _ ->
                FirebaseAuth.getInstance().currentUser?.sendEmailVerification()
                    ?.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            showMessage("Verification email sent")
                        }
                    }
            }
            .setNegativeButton("Close") { _, _ ->
                FirebaseAuth.getInstance().signOut()
            }
            .show()
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
                    viewModel.sendPasswordResetEmail(email) { success, message ->
                        showMessage(message ?: "")
                    }
                } else {
                    showMessage("Please enter your email")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun validateFields(): Boolean {
        val email = binding.editLoginEmail.text.toString()
        val password = binding.editLoginPassword.text.toString()

        if (email.isEmpty()) {
            binding.textInputLayoutLoginEmail.error = "Enter your e-mail"
            return false
        }
        binding.textInputLayoutLoginEmail.error = null

        if (password.isEmpty()) {
            binding.textInputLayoutLoginPassword.error = "Enter your password"
            return false
        }
        binding.textInputLayoutLoginPassword.error = null

        return true
    }
}