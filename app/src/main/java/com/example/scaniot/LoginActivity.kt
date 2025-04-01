package com.example.scaniot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
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

        //MY CODES
        setContentView( binding.root )
        initializeClickEvents()
        //firebaseAuth.signOut()

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
        if (myCurrentUser != null ){
            startActivity(
                Intent(this, DashboardScreenActivity::class.java )
            )
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
    }

    private fun loginUser() {
        firebaseAuth.signInWithEmailAndPassword(
            email, password
        ).addOnSuccessListener {
            startActivity(
                Intent(this, DashboardScreenActivity::class.java )
            )
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