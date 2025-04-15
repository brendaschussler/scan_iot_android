package com.example.scaniot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.scaniot.databinding.ActivityRegisterBinding
import com.example.scaniot.utils.showMessage
import com.example.scaniot.viewModel.RegisterViewModel

class RegisterActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityRegisterBinding.inflate(layoutInflater)
    }

    private val viewModel: RegisterViewModel by viewModels()

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

    private fun initializeClickEvents() {
        binding.textSignIn.setOnClickListener {
            finish() // return to login activity (parent activity)
        }

        binding.btnSignUp.setOnClickListener {
            viewModel.name = binding.editName.text.toString()
            viewModel.email = binding.editEmail.text.toString()
            viewModel.password = binding.editPassword.text.toString()
            viewModel.confirmPassword = binding.editConfirmPassword.text.toString()

            if (validateFields()) {
                registerUser()
            }
        }
    }

    private fun registerUser() {
        viewModel.registerUser(
            onSuccess = {
                showMessage("Your account has been successfully created.")
                finish() // back to login screen
            },
            onWeakPassword = {
                showMessage("Weak password.")
            },
            onUserCollision = {
                showMessage("The user already exists.")
            },
            onInvalidCredentials = {
                showMessage("Invalid credentials.")
            },
            onFailure = { e ->
                showMessage("Registration error: ${e.message}")
            }
        )
    }

    private fun validateFields(): Boolean {
        val (fieldId, errorMessage) = viewModel.getFieldValidationErrors()

        if (fieldId != 0) {
            when (fieldId) {
                R.id.textInputLayoutName -> binding.textInputLayoutName.error = errorMessage
                R.id.textInputLayoutEmail -> binding.textInputLayoutEmail.error = errorMessage
                R.id.textInputLayoutPassword -> binding.textInputLayoutPassword.error = errorMessage
                R.id.textInputLayoutConfirmPassword ->
                    binding.textInputLayoutConfirmPassword.error = errorMessage
            }
            return false
        }

        // Clear all errors if validation passes
        binding.textInputLayoutName.error = null
        binding.textInputLayoutEmail.error = null
        binding.textInputLayoutPassword.error = null
        binding.textInputLayoutConfirmPassword.error = null
        return true
    }
}