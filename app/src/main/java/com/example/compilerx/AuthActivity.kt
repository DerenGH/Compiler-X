package com.example.compilerx

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
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
    }

    override fun onStart() {
        super.onStart()
        val currentUser = mAuth.currentUser
        if (currentUser != null && currentUser.isEmailVerified) {
            goToMainApp()
        }
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