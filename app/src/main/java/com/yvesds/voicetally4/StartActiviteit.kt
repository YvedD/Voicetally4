package com.yvesds.voicetally4

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.yvesds.voicetally4.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Launcher-activity:
 * - Hilt-ready
 * - Past Day/Night toe via SharedPreferences (injectie via Hilt)
 * - Toont NavHost (activity_main.xml) met StartScherm als startDestination
 * - Edge-to-edge met nette insets-handling
 */
@AndroidEntryPoint
class StartActiviteit : AppCompatActivity() {

    @Inject lateinit var sharedPrefs: SharedPreferences

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge zonder Compose
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Thema instellen vóór super.onCreate() om UI-flicker te vermijden
        applyThemeFromPrefs()

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // System bar insets doorgeven aan de root container
        applyEdgeToEdgeInsets(binding.root)
        // NavHost zit in activity_main.xml -> nav_graph -> StartScherm
    }

    private fun applyThemeFromPrefs() {
        when (sharedPrefs.getString(SettingsKeys.KEY_THEME_MODE, SettingsKeys.THEME_DARK)) {
            SettingsKeys.THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            SettingsKeys.THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) // default: donker
        }
    }

    private fun applyEdgeToEdgeInsets(root: View) {
        // Bewaar initiële padding zodat we system bar insets erbij kunnen optellen
        val initialPaddingLeft = root.paddingLeft
        val initialPaddingTop = root.paddingTop
        val initialPaddingRight = root.paddingRight
        val initialPaddingBottom = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                initialPaddingLeft + bars.left,
                initialPaddingTop + bars.top,
                initialPaddingRight + bars.right,
                initialPaddingBottom + bars.bottom
            )
            insets
        }
        // Vraag meteen een nieuwe insets pass aan voor huidige view-hiërarchie
        root.requestApplyInsets()
    }

    companion object {
        /**
         * API-compatibiliteit: laat bestaande referenties naar StartActiviteit.PREFS_NAME/KEY_*
         * gewoon doorverwijzen naar SettingsKeys. Markeer als deprecated.
         */
        @Deprecated(
            message = "Gebruik SettingsKeys.PREFS_NAME",
            replaceWith = ReplaceWith("SettingsKeys.PREFS_NAME")
        )
        const val PREFS_NAME: String = SettingsKeys.PREFS_NAME

        @Deprecated(
            message = "Gebruik SettingsKeys.KEY_PERMISSIONS_DONE",
            replaceWith = ReplaceWith("SettingsKeys.KEY_PERMISSIONS_DONE")
        )
        const val KEY_PERMISSIONS_DONE: String = SettingsKeys.KEY_PERMISSIONS_DONE

        @Deprecated(
            message = "Gebruik SettingsKeys.KEY_THEME_MODE",
            replaceWith = ReplaceWith("SettingsKeys.KEY_THEME_MODE")
        )
        const val KEY_THEME_MODE: String = SettingsKeys.KEY_THEME_MODE

        @Deprecated(
            message = "Gebruik SettingsKeys.THEME_DARK",
            replaceWith = ReplaceWith("SettingsKeys.THEME_DARK")
        )
        const val THEME_DARK: String = SettingsKeys.THEME_DARK

        @Deprecated(
            message = "Gebruik SettingsKeys.THEME_LIGHT",
            replaceWith = ReplaceWith("SettingsKeys.THEME_LIGHT")
        )
        const val THEME_LIGHT: String = SettingsKeys.THEME_LIGHT

        @Deprecated(
            message = "Gebruik SettingsKeys.THEME_SYSTEM",
            replaceWith = ReplaceWith("SettingsKeys.THEME_SYSTEM")
        )
        const val THEME_SYSTEM: String = SettingsKeys.THEME_SYSTEM
    }
}

/**
 * Gecentraliseerde sleutels/waarden voor instellingen.
 * Bewust hier gehouden (zelfde bestand) om extra projectwijzigingen te vermijden.
 */
object SettingsKeys {
    const val PREFS_NAME = "voicetally_prefs"

    // Flags
    const val KEY_PERMISSIONS_DONE = "permissions_done_once"

    // Thema
    const val KEY_THEME_MODE = "theme_mode" // "dark" | "light" | "system"
    const val THEME_DARK = "dark"
    const val THEME_LIGHT = "light"
    const val THEME_SYSTEM = "system"
}
