package com.example.scaniot.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val isEmailVerified: Boolean) : LoginState()
    data class Error(val message: String) : LoginState()
    object EmailNotVerified : LoginState()
}

class LoginViewModel(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) : ViewModel() {
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    fun loginUser(email: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val authResult = auth.signInWithEmailAndPassword(email, password).await()
                val user = authResult.user

                if (user?.isEmailVerified == true) {
                    _loginState.value = LoginState.Success(true)
                } else {
                    user?.sendEmailVerification()?.await()
                    _loginState.value = LoginState.EmailNotVerified
                    auth.signOut() // Força logout se email não estiver verificado
                }
            } catch (e: Exception) {
                _loginState.value = when (e) {
                    is FirebaseAuthInvalidUserException -> LoginState.Error("Invalid e-mail.")
                    is FirebaseAuthInvalidCredentialsException -> LoginState.Error("Invalid e-mail or password.")
                    else -> LoginState.Error(e.message ?: "Login failed")
                }
            }
        }
    }

    fun sendPasswordResetEmail(email: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                auth.sendPasswordResetEmail(email).await()
                onComplete(true, "Password reset link sent to $email")
            } catch (e: Exception) {
                val message = if (e is FirebaseAuthInvalidUserException) {
                    "Email not registered"
                } else {
                    "Failed: ${e.message}"
                }
                onComplete(false, message)
            }
        }
    }

    fun checkUserLoggedIn(): Boolean {
        val user = auth.currentUser
        return user != null && user.isEmailVerified
    }
}