package com.example.compilerx

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottom_navigation)

        if (savedInstanceState == null) {
            loadFragment(CodeFragment())
            bottomNav.selectedItemId = R.id.nav_code
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_code -> {
                    loadFragment(CodeFragment())
                    true
                }
                R.id.nav_chat -> {
                    loadFragment(QuizFragment())
                    true
                }
                R.id.nav_settings -> {
                    loadFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onResume() {
        super.onResume()
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment is CodeFragment) {
            bottomNav.selectedItemId = R.id.nav_code
        } else if (currentFragment is SettingsFragment) {
            bottomNav.selectedItemId = R.id.nav_settings
        }
    }
}