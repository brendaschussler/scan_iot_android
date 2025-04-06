package com.example.scaniot

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.scaniot.databinding.ActivityLoginBinding
import com.example.scaniot.utils.showMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class LoginActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityLoginBinding.inflate( layoutInflater )
    }

    private lateinit var email: String
    private lateinit var password: String

    private val firebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView( binding.root )
        initializeClickEvents()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onStart() {
        super.onStart()
        verifyUserIsLoggedIn()
    }

    private fun verifyUserIsLoggedIn() {
        val myCurrentUser = firebaseAuth.currentUser
        if(myCurrentUser!=null) {
            if (myCurrentUser.isEmailVerified) {
                startActivity(Intent(this, DashboardScreenActivity::class.java))
            } else {
                showEmailRequiredAlert()
            }
        }
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
            .setPositiveButton("Resend Verfication Email") { _, _ ->
                sendVerificationEmail()
            }
            .setNegativeButton("Close") { _, _ ->
                FirebaseAuth.getInstance().signOut()
            }
            .show()
    }

    private fun sendVerificationEmail() {
        val user = firebaseAuth.currentUser
        user?.sendEmailVerification()
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("EmailVerification", "email sent")
                } else {
                    Log.e("EmailVerification", "failed to send email", task.exception)
                }
            }
    }

    private fun initializeClickEvents() {
        binding.textSignUp.setOnClickListener {
            startActivity(
                Intent(this, RegisterActivity::class.java)
            )
        }
        binding.btnLogin.setOnClickListener {
            if (validateFields()){
                loginUser()
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
                    sendPasswordResetEmail(email)
                } else {
                    showMessage("Please enter your email")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendPasswordResetEmail(email: String) {
        firebaseAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    showMessage("Password reset link sent to $email")
                } else {
                    showMessage("Failed: ${task.exception?.message}")
                }
            }.addOnFailureListener { e ->
                if (e is FirebaseAuthInvalidUserException) {
                    showMessage("Email not registered")
                }
            }
    }

    private fun loginUser() {
        firebaseAuth.signInWithEmailAndPassword(
            email, password
        ).addOnSuccessListener {
            if (firebaseAuth.currentUser?.isEmailVerified == true) {
                // Login bem-sucedido
                startActivity(Intent(this, DashboardScreenActivity::class.java))
            } else {
                // Mostra alerta
                showVerificationAlert()
                FirebaseAuth.getInstance().signOut() // Força logout
            }
        }.addOnFailureListener { myError ->
            try {
                throw myError
            } catch( invalidUserError: FirebaseAuthInvalidUserException) {
                invalidUserError.printStackTrace()
                showMessage("Invalid e-mail.")
            } catch( invalidCredentialsError: FirebaseAuthInvalidCredentialsException){
                invalidCredentialsError.printStackTrace()
                showMessage("Invalid e-mail or password.")
            }
        }
    }

    private fun validateFields(): Boolean {
        email = binding.editLoginEmail.text.toString()
        password = binding.editLoginPassword.text.toString()

        if ( email.isNotEmpty() ){
            binding.textInputLayoutLoginEmail.error = null
            if ( password.isNotEmpty() ){
                binding.textInputLayoutLoginPassword.error = null
                return true
            }else{
                binding.textInputLayoutLoginPassword.error = "Enter your password"
                return false
            }
        }else{
            binding.textInputLayoutLoginEmail.error = "Enter your e-mail"
            return false
        }
    }


}