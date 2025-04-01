package com.example.scaniot

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.scaniot.databinding.ActivityLoginBinding
import com.example.scaniot.databinding.ActivityRegisterBinding
import com.example.scaniot.model.User
import com.example.scaniot.utils.showMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityRegisterBinding.inflate( layoutInflater )
    }

    private lateinit var name: String
    private lateinit var email: String
    private lateinit var password: String

    private val firebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }
    private val firestore by lazy {
        FirebaseFirestore.getInstance()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        //MY CODES
        setContentView( binding.root )
        initializeClickEvents()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initializeClickEvents() {
        binding.textSignIn.setOnClickListener {
            finish() //return to login activity (parent activity)
        }

        binding.btnSignUp.setOnClickListener {
            if ( validateFields() ){
                registerUser(name, email, password)
                
            }

        }
    }

    private fun registerUser(name: String, email: String, password: String) {
        firebaseAuth.createUserWithEmailAndPassword(
            email, password
        ).addOnCompleteListener { result ->
            if (result.isSuccessful){

                //save data on Firestore
                val userId = result.result.user?.uid
                if (userId != null){
                    val user = User(
                        userId, name, email
                    )
                    saveUserFirestore(user)
                }

                startActivity(
                    // if register is successful firebase already authenticates the user
                    // don't need to do login
                    Intent(applicationContext, DashboardScreenActivity::class.java)
                )
            }
        }.addOnFailureListener { myError ->
            try {
                throw myError
            }catch (invalidPasswordError: FirebaseAuthWeakPasswordException ){
                invalidPasswordError.printStackTrace()
                showMessage("Weak password.")
            }catch (userAlreadyExistsError: FirebaseAuthUserCollisionException ){
                userAlreadyExistsError.printStackTrace()
                showMessage("The user already exists.")
            }catch (invalidCredentialsError: FirebaseAuthInvalidCredentialsException){
                invalidCredentialsError.printStackTrace()
                showMessage("Invalid credentials.")
            }
        }
    }

    private fun saveUserFirestore(user: User) {
        firestore
            .collection("users")
            .document(user.userId)
            .set(user)
            .addOnSuccessListener {
                showMessage("Your account has been successfully created.")
            }.addOnFailureListener {
                showMessage("Registration error.")
            }
    }

    private fun validateFields(): Boolean {

        name = binding.editName.text.toString()
        email = binding.editEmail.text.toString()
        password = binding.editPassword.text.toString()

        if (name.isNotEmpty()) {
            binding.textInputLayoutName.error = null

            if (email.isNotEmpty()) {
                binding.textInputLayoutEmail.error = null

                if (password.isNotEmpty()) {
                    binding.textInputLayoutPassword.error = null
                    return true
                }else{
                    binding.textInputLayoutPassword.error = "Enter your password"
                    return false
                }
            }else{
                binding.textInputLayoutEmail.error = "Enter your e-mail"
                return false
            }
        }else{
            binding.textInputLayoutName.error = "Enter your name"
            return false
        }

    }

}