package com.example.compilerx

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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

        val currentUser = mAuth.currentUser
        tvUser.text = currentUser?.displayName ?: "Developer"
        tvEmail.text = currentUser?.email ?: "Offline"

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
        val (bgColor, textColor, cardBg, accentColor, innerCardTextColor) = when (themeIndex) {
            0 -> arrayOf("#000000", "#FFFFFF", "#121212", "#4CAF50", "#FFFFFF") // Midnight (Dark)
            1 -> arrayOf("#F0F0F0", "#000000", "#FFFFFF", "#2196F3", "#000000") // High Contrast (Light - Text Fixed)
            2 -> arrayOf("#0D1117", "#9CDCFE", "#161B22", "#58A6FF", "#9CDCFE") // VS Dark (Dark)
            3 -> arrayOf("#1B2B34", "#6699CC", "#233139", "#6699CC", "#6699CC") // Oceanic (Dark)
            else -> arrayOf("#000000", "#FFFFFF", "#121212", "#4CAF50", "#FFFFFF")
        }

        val bgInt = Color.parseColor(bgColor)
        val textInt = Color.parseColor(textColor)
        val cardInt = Color.parseColor(cardBg)
        val accentInt = Color.parseColor(accentColor)
        val innerTextInt = Color.parseColor(innerCardTextColor)

        val root = view.findViewById<View>(R.id.main)
        root?.setBackgroundColor(bgInt)

        val btnLogOut = view.findViewById<Button>(R.id.btnLogOut)
        btnLogOut?.apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.RED)
            setTextColor(Color.WHITE)
        }

        val cardShape = GradientDrawable().apply {
            cornerRadius = 30f
            setColor(cardInt)
        }
        view.findViewById<View>(R.id.profileCard)?.background = cardShape
        view.findViewById<View>(R.id.visualEngineCard)?.background = cardShape
        view.findViewById<View>(R.id.executionCard)?.background = cardShape

        view.findViewById<TextView>(R.id.settings_title)?.setTextColor(accentInt)

        view.findViewById<TextView>(R.id.labelVisualEngineHeader)?.setTextColor(accentInt)
        view.findViewById<TextView>(R.id.labelExecutionEngineHeader)?.setTextColor(accentInt)

        val generalCardText = listOf(R.id.tvSettingsUsername, R.id.tvSettingsEmail, R.id.labelTheme, R.id.labelFontSize, R.id.labelAutoSave, R.id.labelClearConsole)
        generalCardText.forEach { view.findViewById<TextView>(it)?.setTextColor(textInt) }

        (view.findViewById<Spinner>(R.id.spinnerTheme)?.selectedView as? TextView)?.setTextColor(innerTextInt)
        (view.findViewById<Spinner>(R.id.spinnerFontSize)?.selectedView as? TextView)?.setTextColor(innerTextInt)

        val activityNav = requireActivity().findViewById<View>(R.id.bottom_navigation)
        activityNav?.let { nav ->
            val background = nav.background
            if (background is GradientDrawable) {
                background.setColor(bgInt)
            } else {
                val newShape = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadii = floatArrayOf(45f, 45f, 45f, 45f, 0f, 0f, 0f, 0f)
                    setColor(bgInt)
                    setStroke(2, Color.parseColor("#33FFFFFF"))
                }
                nav.background = newShape
            }
        }
    }
}