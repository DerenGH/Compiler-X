package com.example.compilerx

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth

class SettingsFragment : Fragment() {

    private lateinit var mAuth: FirebaseAuth
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mAuth = FirebaseAuth.getInstance()
        prefs = requireActivity().getSharedPreferences("CompilerX_Prefs", Context.MODE_PRIVATE)

        val tvUser = view.findViewById<TextView>(R.id.tvSettingsUsername)
        val tvEmail = view.findViewById<TextView>(R.id.tvSettingsEmail)
        val btnLogOut = view.findViewById<Button>(R.id.btnLogOut)
        val switchAutoSave = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchAutoSave)
        val switchClearConsole = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchClearConsole)

        // Check if user entered via Guest Mode
        val isGuest = prefs.getBoolean("is_guest", false)

        if (isGuest) {
            tvUser.text = "Guest"
            tvEmail.text = "Offline Mode"
        } else {
            val currentUser = mAuth.currentUser
            tvUser.text = currentUser?.displayName ?: "Developer"
            tvEmail.text = currentUser?.email ?: "Offline"
        }

        switchAutoSave.isChecked = prefs.getBoolean("auto_save", false)
        switchClearConsole.isChecked = prefs.getBoolean("clear_console", true)

        switchAutoSave.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_save", isChecked).apply()
        }
        switchClearConsole.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("clear_console", isChecked).apply()
        }

        btnLogOut.setOnClickListener {
            mAuth.signOut()
            // Clear the local state flag when logging out
            prefs.edit().putBoolean("is_guest", false).apply()

            startActivity(Intent(requireContext(), AuthActivity::class.java))
            requireActivity().finishAffinity()
        }

        setupSpinners(view)
        applySettings(view)
    }

    private fun setupSpinners(view: View) {
        val fontSizeSpinner = view.findViewById<Spinner>(R.id.spinnerFontSize)
        val themeSpinner = view.findViewById<Spinner>(R.id.spinnerTheme)

        val sizes = arrayOf("12sp", "14sp", "16sp", "18sp", "22sp")
        val themes = arrayOf("Midnight", "High Contrast", "VS Dark", "Oceanic")

        fontSizeSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, sizes)
        themeSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, themes)

        fontSizeSpinner.setSelection(prefs.getInt("font_size_index", 1))
        themeSpinner.setSelection(prefs.getInt("theme_index", 0))

        fontSizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                prefs.edit().putInt("font_size_index", pos).apply()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        themeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                prefs.edit().putInt("theme_index", pos).apply()
                applySettings(view)
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun applySettings(view: View) {
        val themeIndex = prefs.getInt("theme_index", 0)

        // Updated Soft-Dark hex values to correct the transparent layout issue and align selection hues
        val (bgColor, textColor, cardBg, accentColor, innerCardTextColor) = when (themeIndex) {
            0 -> arrayOf("#121212", "#FFFFFF", "#1E1E1E", "#4CAF50", "#FFFFFF") // Midnight (Green)
            1 -> arrayOf("#F0F0F0", "#000000", "#FFFFFF", "#2196F3", "#000000") // High Contrast (Blue)
            2 -> arrayOf("#0D1117", "#9CDCFE", "#161B22", "#BD93F9", "#9CDCFE") // VS Dark (Purple)
            3 -> arrayOf("#1B2B34", "#6699CC", "#233139", "#6699CC", "#6699CC") // Oceanic (Cyan)
            else -> arrayOf("#121212", "#FFFFFF", "#1E1E1E", "#4CAF50", "#FFFFFF")
        }

        val bgInt = Color.parseColor(bgColor)
        val textInt = Color.parseColor(textColor)
        val cardInt = Color.parseColor(cardBg)
        val accentInt = Color.parseColor(accentColor)
        val innerTextInt = Color.parseColor(innerCardTextColor)

        // 1. Base Container Background
        view.findViewById<View>(R.id.main)?.setBackgroundColor(bgInt)

        // 2. Individual Content Cards - Fixed Rounded Borders
        val cardRadius = 60f
        val cardStrokeColor = Color.parseColor("#33FFFFFF")

        fun getCardDrawable() = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cardRadius
            setColor(cardInt)
            setStroke(3, cardStrokeColor)
        }

        view.findViewById<View>(R.id.profileCard)?.background = getCardDrawable()
        view.findViewById<View>(R.id.visualEngineCard)?.background = getCardDrawable()
        view.findViewById<View>(R.id.executionCard)?.background = getCardDrawable()

        // 3. Section Label Typography Updates
        view.findViewById<TextView>(R.id.settings_title)?.setTextColor(accentInt)
        view.findViewById<TextView>(R.id.labelVisualEngineHeader)?.setTextColor(accentInt)
        view.findViewById<TextView>(R.id.labelExecutionEngineHeader)?.setTextColor(accentInt)

        val labels = listOf(R.id.tvSettingsUsername, R.id.tvSettingsEmail, R.id.labelTheme, R.id.labelFontSize, R.id.labelAutoSave, R.id.labelClearConsole)
        labels.forEach { view.findViewById<TextView>(it)?.setTextColor(textInt) }

        // 4. Input Controls Theme Synchronization
        (view.findViewById<Spinner>(R.id.spinnerTheme)?.selectedView as? TextView)?.setTextColor(innerTextInt)
        (view.findViewById<Spinner>(R.id.spinnerFontSize)?.selectedView as? TextView)?.setTextColor(innerTextInt)

        // 5. Solid Floating Navigation Layout Configuration
        val activityNav = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
        activityNav?.let { nav ->
            val navRadius = 80f
            val navShape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(navRadius, navRadius, navRadius, navRadius, 0f, 0f, 0f, 0f)
                setColor(cardInt) // Set solid background color to fix transparency bugs
                setStroke(4, cardStrokeColor)
            }
            nav.background = navShape
            nav.elevation = 20f

            // Create selection states dynamically so icons shift color cleanly to match your themes
            val itemStates = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(accentInt, Color.parseColor("#8B949E"))
            )
            nav.itemIconTintList = itemStates
            nav.itemTextColor = itemStates
        }
    }
}