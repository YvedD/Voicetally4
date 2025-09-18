package com.yvesds.voicetally4

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.navigation.fragment.NavHostFragment
import com.yvesds.voicetally4.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Launcher-activity:
 * - Hilt-ready
 * - Past Day/Night toe via SharedPreferences
 * - Toont NavHost (activity_main.xml)
 * - Intercepteert BT-HID / hardware KEYCODE_VOLUME_UP en delegeert naar huidig Fragment
 *   (alleen TellenScherm zal dit consumeren via [HardwareKeyHandler]).
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

        // NavHost zit in activity_main.xml
        // (Graph kan via XML of elders ingesteld worden; hier doen we geen extra werk.)
    }

    /**
     * Intercepteer KEYCODE_VOLUME_UP zodat BT-HID (volume-up) als "start luisterronde" kan dienen.
     * - Alleen ACTION_DOWN en repeatCount == 0 (geen autorepeat).
     * - We zoeken het huidige child-fragment van de NavHost en roepen, indien aanwezig,
     *   de HardwareKeyHandler-hook aan.
     * - Bij 'true' (geconsumeerd) voorkomen we volumeverandering en system-UI.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP &&
            event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0
        ) {
            val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as? NavHostFragment
            val current = navHost?.childFragmentManager?.primaryNavigationFragment
            if (current is HardwareKeyHandler && current.onHardwareVolumeUp()) {
                return true // geconsumeerd: geen systeem-volume actie
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun applyThemeFromPrefs() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        when (prefs.getString(KEY_THEME_MODE, THEME_DARK)) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) // default: donker
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

/**
 * Fragments die hardware/BT volume-up willen verwerken (bv. TellenScherm) implementeren deze.
 * Retourneer 'true' als het event geconsumeerd is (dan onderdrukken we systeem-volume).
 */
interface HardwareKeyHandler {
    fun onHardwareVolumeUp(): Boolean
}
