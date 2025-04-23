package com.example.scaniot.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.scaniot.R
import com.example.scaniot.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore

class RegisterViewModel : ViewModel() {

    private val firebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    private val firestore by lazy {
        FirebaseFirestore.getInstance()
    }

    var name: String = ""
    var email: String = ""
    var password: String = ""
    var confirmPassword: String = ""

    fun registerUser(
        onSuccess: () -> Unit,
        onWeakPassword: () -> Unit,
        onUserCollision: () -> Unit,
        onInvalidCredentials: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { result ->
                if (result.isSuccessful) {
                    sendVerificationEmail()
                    val userId = result.result.user?.uid
                    if (userId != null) {
                        saveUserFirestore(
                            User(userId, name, email),
                            onSuccess = { onSuccess() },
                            onFailure = { onFailure(Exception("Firestore save failed")) }
                        )
                    }
                }
            }.addOnFailureListener { myError ->
                try {
                    throw myError
                } catch (invalidPasswordError: FirebaseAuthWeakPasswordException) {
                    onWeakPassword()
                } catch (userAlreadyExistsError: FirebaseAuthUserCollisionException) {
                    onUserCollision()
                } catch (invalidCredentialsError: FirebaseAuthInvalidCredentialsException) {
                    onInvalidCredentials()
                } catch (e: Exception) {
                    onFailure(e)
                }
            }
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

    private fun saveUserFirestore(
        user: User,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        firestore
            .collection("users")
            .document(user.userId)
            .set(user)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure() }
    }

    fun validateFields(): Boolean {
        return when {
            name.isEmpty() -> false
            email.isEmpty() -> false
            password.isEmpty() -> false
            password != confirmPassword -> false
            else -> true
        }
    }

    fun getFieldValidationErrors(): Pair<Int, String?> {
        return when {
            name.isEmpty() -> Pair(R.id.textInputLayoutName, "Enter your name")
            email.isEmpty() -> Pair(R.id.textInputLayoutEmail, "Enter your e-mail")
            password.isEmpty() -> Pair(R.id.textInputLayoutPassword, "Enter your password")
            password != confirmPassword -> Pair(
                R.id.textInputLayoutConfirmPassword,
                "Password and confirm password does not match")
            else -> Pair(0, null)
        }
    }
}