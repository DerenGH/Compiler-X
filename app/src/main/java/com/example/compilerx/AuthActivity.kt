package com.example.compilerx

import android.content.Context
import android.content.Intent
import android.graphics.Color // Added this import to fix 'Unresolved reference Color'
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

class AuthActivity : AppCompatActivity() {

    private lateinit var mAuth: FirebaseAuth
    private var isLoginMode = true

    private lateinit var authHeader: TextSwitcher
    private lateinit var btnAuthAction: Button
    private lateinit var etUsername: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPass: EditText
    private lateinit var etRepeatPass: EditText
    private lateinit var tvModeToggle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        mAuth = FirebaseAuth.getInstance()

        authHeader = findViewById(R.id.authHeader)
        btnAuthAction = findViewById(R.id.btnAuthAction)
        etUsername = findViewById(R.id.etAuthUsername)
        etEmail = findViewById(R.id.etAuthEmail)
        etPass = findViewById(R.id.etAuthPassword)
        etRepeatPass = findViewById(R.id.etAuthRepeatPassword)
        tvModeToggle = findViewById(R.id.tvAuthModeToggle)

        setupTextSwitcherAnimations()

        btnAuthAction.setOnClickListener { handleAuthAction() }
        tvModeToggle.setOnClickListener { toggleAuthMode() }

        setupGuestModeButton()
    }

    override fun onStart() {
        super.onStart()
        val prefs = getSharedPreferences("CompilerX_Prefs", Context.MODE_PRIVATE)
        val isGuest = prefs.getBoolean("is_guest", false)

        val currentUser = mAuth.currentUser
        if (isGuest || (currentUser != null && currentUser.isEmailVerified)) {
            goToMainApp()
        }
    }

    private fun setupGuestModeButton() {
        // Target the parent ConstraintLayout from your activity_auth.xml
        val rootLayout = tvModeToggle.parent as? androidx.constraintlayout.widget.ConstraintLayout ?: return

        val tvGuestMode = TextView(this).apply {
            id = View.generateViewId() // Create a fresh unique ID for constraint layout mapping
            text = "Continue as Guest"
            textSize = 14f

            // FIX: This replaces the broken .fontFamily property with correct Android Typeface mapping
            typeface = android.graphics.Typeface.MONOSPACE

            setTextColor(Color.parseColor("#40FFFFFF")) // Gives it a clean muted look matching your other toggle text
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(0, 20, 0, 20)

            setOnClickListener {
                val prefs = getSharedPreferences("CompilerX_Prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("is_guest", true).apply()

                mAuth.signOut()
                goToMainApp()
            }
        }

        // Configure strict layout constraints to attach cleanly beneath your mode toggle button
        val layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT,
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topToBottom = tvModeToggle.id
            startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            topMargin = 24 // Gives it a precise spacing break underneath
        }

        rootLayout.addView(tvGuestMode, layoutParams)

        // FIX: Forces the ConstraintLayout engine to redraw and recognize the newly injected view instantly
        rootLayout.requestLayout()
    }

    private fun handleAuthAction() {
        val email = etEmail.text.toString().trim()
        val password = etPass.text.toString().trim()
        val repeatPass = etRepeatPass.text.toString().trim()
        val username = etUsername.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Complete all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (isLoginMode) {
            loginUser(email, password)
        } else {
            if (username.isEmpty()) {
                Toast.makeText(this, "Enter a username", Toast.LENGTH_SHORT).show()
                return
            }
            if (password != repeatPass) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return
            }
            registerUser(email, password, username)
        }
    }

    private fun loginUser(email: String, pass: String) {
        mAuth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = mAuth.currentUser
                if (user != null && user.isEmailVerified) {
                    val prefs = getSharedPreferences("CompilerX_Prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("is_guest", false).apply()
                    goToMainApp()
                } else {
                    Toast.makeText(this, "Verify your email first", Toast.LENGTH_LONG).show()
                    mAuth.signOut()
                }
            } else {
                Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun registerUser(email: String, pass: String, username: String) {
        mAuth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = mAuth.currentUser
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(username)
                    .build()

                user?.updateProfile(profileUpdates)
                user?.sendEmailVerification()

                Toast.makeText(this, "Verification sent to $email", Toast.LENGTH_LONG).show()
                toggleAuthMode()
            } else {
                Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleAuthMode() {
        isLoginMode = !isLoginMode

        val extraVisibility = if (isLoginMode) View.GONE else View.VISIBLE
        etUsername.visibility = extraVisibility
        etRepeatPass.visibility = extraVisibility

        if (isLoginMode) {
            authHeader.setText("LOG IN")
            btnAuthAction.text = "LOG IN"
            tvModeToggle.text = "Not Registered? (CREATE ACCOUNT)"
        } else {
            authHeader.setText("REGISTER")
            btnAuthAction.text = "REGISTER"
            tvModeToggle.text = "Already Registered? (LOG IN)"
        }
    }

    private fun setupTextSwitcherAnimations() {
        authHeader.setInAnimation(this, android.R.anim.fade_in)
        authHeader.setOutAnimation(this, android.R.anim.fade_out)
    }

    private fun goToMainApp() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}