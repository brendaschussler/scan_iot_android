package com.example.scaniot.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class LoginViewModel : ViewModel() {

    private val firebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    var email: String = ""
    var password: String = ""

    fun verifyUserIsLoggedIn(onVerified: () -> Unit, onNotVerified: () -> Unit) {
        val myCurrentUser = firebaseAuth.currentUser
        if (myCurrentUser != null) {
            if (myCurrentUser.isEmailVerified) {
                onVerified()
            } else {
                onNotVerified()
            }
        }
    }

    fun loginUser(
        onSuccessVerified: () -> Unit,
        onSuccessNotVerified: () -> Unit,
        onInvalidEmail: () -> Unit,
        onInvalidCredentials: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                if (firebaseAuth.currentUser?.isEmailVerified == true) {
                    onSuccessVerified()
                } else {
                    onSuccessNotVerified()
                    FirebaseAuth.getInstance().signOut()
                }
            }
            .addOnFailureListener { myError ->
                try {
                    throw myError
                } catch (invalidUserError: FirebaseAuthInvalidUserException) {
                    invalidUserError.printStackTrace()
                    onInvalidEmail()
                } catch (invalidCredentialsError: FirebaseAuthInvalidCredentialsException) {
                    invalidCredentialsError.printStackTrace()
                    onInvalidCredentials()
                } catch (e: Exception) {
                    onFailure(e)
                }
            }
    }

    fun sendPasswordResetEmail(
        email: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit,
        onInvalidUser: () -> Unit
    ) {
        firebaseAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onSuccess("Password reset link sent to $email")
                } else {
                    onFailure("Failed: ${task.exception?.message}")
                }
            }.addOnFailureListener { e ->
                if (e is FirebaseAuthInvalidUserException) {
                    onInvalidUser()
                }
            }
    }
}