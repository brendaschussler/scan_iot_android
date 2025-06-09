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
    var lastName: String = ""
    var country: String = ""
    var email: String = ""
    var password: String = ""
    var confirmPassword: String = ""
    var organization: String = ""
    var jobTitle: String = ""
    var manager: String = ""

    fun isEmailInstitutional(): Boolean {
        if (organization.isEmpty()) return true // No organization, personal email is allowed

        val personalEmailDomains = listOf(
            "gmail.com",
            "yahoo.com",
            "hotmail.com",
            "outlook.com",
            "icloud.com",
            "aol.com",
            "protonmail.com",
            "mail.com",
            "zoho.com",
            "yandex.com",
            "yandex.ru",
            "gmx.com",
            "tutanota.com",
            "fastmail.com",
            "mail.ru",
            "rambler.ru",
            "inbox.com",
            "hushmail.com",
            "rediffmail.com",

            "naver.com",
            "daum.net",
            "qq.com",
            "163.com",
            "126.com",
            "sina.com",
            "sohu.com",

            "web.de",
            "gmx.de",
            "t-online.de",
            "orange.fr",
            "wanadoo.fr",
            "laposte.net",
            "sfr.fr",
            "free.fr",
            "libero.it",
            "seznam.cz",
            "wp.pl",
            "onet.pl",
            "interia.pl",
            "o2.pl",
            "bk.ru",
            "inbox.ru",
            "list.ru",

            "bol.com.br",
            "yahoo.com.br",
            "uol.com.br",
            "ig.com.br",
            "terra.com.br",
            "globo.com",
            "zipmail.com.br",
            "superig.com.br",

            "live.com",
            "msn.com",
            "me.com",
            "mac.com",
            "rocketmail.com",
            "att.net",
            "verizon.net",
            "juno.com",
            "earthlink.net",
            "cox.net",
            "comcast.net",

            "ntlworld.com",
            "btinternet.com",
            "virginmedia.com",
            "blueyonder.co.uk",
            "sky.com",

            "bigpond.com",
            "optusnet.com.au",
            "telstra.com",
            "xtra.co.nz"
        )

        val emailDomain = email.substringAfterLast('@').lowercase()
        Log.d("EMAIL", "emailDomain: $emailDomain")

        return !personalEmailDomains.any { emailDomain.endsWith(it) }
    }

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
                            User(
                                userId,
                                name,
                                lastName,
                                country,
                                email,
                                if (organization.isEmpty()) null else organization,
                                if (jobTitle.isEmpty()) null else jobTitle,
                                if (manager.isEmpty()) null else manager
                            ),
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

    fun getFieldValidationErrors(): Pair<Int, String?> {
        return when {
            name.isEmpty() -> Pair(R.id.textInputLayoutName, "Enter your first name")
            lastName.isEmpty() -> Pair(R.id.textInputLayoutLastName, "Enter your last name")
            country.isEmpty() -> Pair(R.id.textInputLayoutCountry, "Enter your country")
            email.isEmpty() -> Pair(R.id.textInputLayoutEmail, "Enter your e-mail")
            password.isEmpty() -> Pair(R.id.textInputLayoutPassword, "Enter your password")
            password != confirmPassword -> Pair(
                R.id.textInputLayoutConfirmPassword,
                "Password and confirm password does not match")
            organization.isNotEmpty() && jobTitle.isEmpty() ->
                Pair(R.id.textInputLayoutJobTitle, "Job title is required when organization is provided")
            organization.isNotEmpty() && manager.isEmpty() ->
                Pair(R.id.textInputLayoutManager, "Manager/Supervisor name is required when organization is provided")
            else -> Pair(0, null)
        }
    }
}