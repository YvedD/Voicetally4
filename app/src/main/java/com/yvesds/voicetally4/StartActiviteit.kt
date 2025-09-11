package com.yvesds.voicetally4

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import com.yvesds.voicetally4.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Launcher-activity:
 * - Hilt-ready
 * - Past Day/Night toe via SharedPreferences
 * - Toont NavHost (activity_main.xml) met StartScherm als startDestination
 */
@AndroidEntryPoint
class StartActiviteit : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge zonder Compose
        WindowCompat.setDecorFitsSystemWindows(window, false)

        applyThemeFromPrefs()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // NavHost zit in activity_main.xml -> nav_graph -> StartScherm
    }

    private fun applyThemeFromPrefs() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        when (prefs.getString(KEY_THEME_MODE, THEME_DARK)) {
            THEME_LIGHT  -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            else         -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) // default: donker
        }
    }

    companion object {
        const val PREFS_NAME = "voicetally_prefs"
        const val KEY_PERMISSIONS_DONE = "permissions_done_once"
        const val KEY_THEME_MODE = "theme_mode" // "dark" | "light" | "system"
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"
        const val THEME_SYSTEM = "system"
    }
}
