package com.example.scaniot

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.scaniot.databinding.ActivityDashboardScreenBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DashboardScreenActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityDashboardScreenBinding.inflate( layoutInflater )
    }

    private val firebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    private val firestore by lazy {
        FirebaseFirestore.getInstance()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView( binding.root )

        initializeToolbar()
        initializeClickEvents()
        fetchUserNameFromFirestore()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun fetchUserNameFromFirestore() {
        val userId = firebaseAuth.currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                val name = document.getString("name") ?: "User"
                binding.textNameUser.text = name
            }
            .addOnFailureListener {
                binding.textNameUser.text = "User"
            }
    }

    private fun navigateToScan(){
        binding.btnScanDevices.setOnClickListener {
            startActivity(
                Intent(this, ScanDevicesActivity::class.java)
            )
        }
    }

    private fun navigateToSavedDevices(){
        binding.btnSavedDevices.setOnClickListener {
            startActivity(
                Intent(this, SavedDevicesActivity::class.java)
            )
        }
    }

    private fun navigateToCapturedPackets() {
        binding.btnCapturedPackets.setOnClickListener {
            val intent = Intent(this, CapturedPacketsActivity::class.java)
            intent.putExtra("force_refresh", true)
            startActivity(intent)
        }
    }

    private fun initializeClickEvents() {
        navigateToScan()
        navigateToSavedDevices()
        navigateToCapturedPackets()
        binding.btnSystemRequirements.setOnClickListener {
            showSystemRequirementsDialog()
        }
    }

    private fun showSystemRequirementsDialog() {
        val linkText = "https://f-droid.org/"
        val commandsText = "pkg update -y && pkg upgrade -y && pkg install root-repo -y && pkg install iproute2 -y && pkg install tcpdump -y && pkg install coreutils -y"

        val tutorialText = """
📱 SYSTEM REQUIREMENTS:

✔ ROOT access on your device  
✔ Termux installed via F-Droid 
✔ Termux packages installed

🔧 How to install Termux via F-Droid:
1. Download F-Droid from: $linkText
2. Open F-Droid and search for “Termux”
3. Install Termux
⚠️ Do NOT install from the Play Store! It is outdated.

📥 How to install required commands in Termux:
Open Termux and paste the command below, then press Enter:

$commandsText

🧪 Root access test in Termux:
Type 'su' in Termux. If the prompt changes to #, root access is working.

📡 IMPORTANT ABOUT PACKET CAPTURE:
👉 Devices must be connected to your phone via Hotspot (Wi-Fi tethering).
✔  Activate the hotspot on the cell phone running the app.
✔  Connect the devices you want to scan and capture packets from to this hotspot.

✅ After this, return to the app and use its features normally.

""".trimIndent()

        // Criando layout principal
        val parentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 30)
        }

        // Criando TextView dentro de ScrollView
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f // Peso para ocupar a maior parte da tela
            )
        }

        val textView = TextView(this).apply {
            text = tutorialText
            textSize = 16f
        }

        scrollView.addView(textView)

        // Criando botões
        val copyLinkButton = Button(this).apply {
            text = "Copy F-Droid link"
            setOnClickListener {
                copyToClipboard("F-Droid link", linkText)
            }
        }

        val copyCommandsButton = Button(this).apply {
            text = "Copy install command"
            setOnClickListener {
                copyToClipboard("Install commands", commandsText)
            }
        }

        // Adicionando tudo ao layout
        parentLayout.addView(scrollView)
        parentLayout.addView(copyLinkButton)
        parentLayout.addView(copyCommandsButton)

        AlertDialog.Builder(this)
            .setTitle("System Requirements")
            .setView(parentLayout)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "$label copied!", Toast.LENGTH_SHORT).show()
    }

    private fun initializeToolbar() {
        val toolbar = binding.includeToolbarMain.tbMain
        setSupportActionBar( toolbar )
        supportActionBar?.apply {
            title = "Droid Scour"
        }

        binding.btnLogout.setOnClickListener {
            logoutUser()
        }

    }

    private fun logoutUser() {

        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to log out")
            .setNegativeButton("Cancel"){dialog, position -> }
            .setPositiveButton("Yes"){dialog, position ->
                firebaseAuth.signOut()
                startActivity(
                    Intent(applicationContext, LoginActivity::class.java)
                )
            }
            .create()
            .show()
    }

}