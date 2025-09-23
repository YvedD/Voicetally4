package com.yvesds.voicetally4

import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.yvesds.voicetally4.databinding.ActivityMainBinding
import com.yvesds.voicetally4.ui.core.SetupManager
import com.yvesds.voicetally4.ui.data.AliasesRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Launcher-activity:
 * - Splash dekt eerste frame (geen flikkering).
 * - Theme-guard: wijzigt night mode alleen als de feitelijke UI-modus afwijkt.
 * - StartDestination enkel bij fresh create (savedInstanceState == null).
 * - E2E insets & timing + reportFullyDrawn().
 * - 🌡️ Start parallel een warme preload van aliases.vt4bin (off-main, time-boxed).
 * - 🟢 Toast verschijnt in het Splash-exitmoment (perceptueel “tijdens/erop” de Splash).
 */
@AndroidEntryPoint
class StartActiviteit : AppCompatActivity() {

    @Inject lateinit var sharedPrefs: SharedPreferences

    private lateinit var binding: ActivityMainBinding
    private var contentReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1) Splash installeren
        val splash: SplashScreen = installSplashScreen()
        splash.setKeepOnScreenCondition { !contentReady }

        // We willen in onExit weten of dit een "verse start" was:
        var isFreshCreate = false

        // ✨ Toon de Toast in het exitmoment + fade de Splash soepel weg
        splash.setOnExitAnimationListener { provider ->
            // Eerst de Toast; die verschijnt terwijl de Splash begint te verdwijnen
            if (isFreshCreate) {
                Toast.makeText(
                    this,
                    getString(R.string.loading_species_msg),
                    Toast.LENGTH_SHORT
                ).show()
            }
            // Korte fade-out van de Splash-view
            val splashView = provider.view
            splashView.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction { provider.remove() }
                .start()
        }

        // 2) Edge-to-edge
        enableEdgeToEdge()

        val t0 = SystemClock.elapsedRealtimeNanos()

        // 3) Hilt + strakke theme-guard vóór inflating
        super.onCreate(savedInstanceState)
        applyThemeFromPrefsGuarded()

        val t1 = SystemClock.elapsedRealtimeNanos()

        // 4) Inflate & setContentView
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdgeInsets(binding.root)

        val t2 = SystemClock.elapsedRealtimeNanos()

        // 5) Navigation: enkel bij eerste creatie graph zetten
        isFreshCreate = savedInstanceState == null
        android.util.Log.i("VT4.Startup", "isFreshCreate=$isFreshCreate")

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as? NavHostFragment
            ?: error("NavHostFragment niet gevonden. Check @id/nav_host in activity_main.xml")
        val navController = navHost.navController

        if (isFreshCreate) {
            val setup = SetupManager(this, sharedPrefs)
            val graph = navController.navInflater.inflate(R.navigation.nav_graph)
            val startDest = if (setup.isSetupDoneFlag()) R.id.opstartScherm else R.id.eersteSetupScherm
            if (graph.startDestinationId != startDest) graph.setStartDestination(startDest)
            navController.graph = graph
            navController.handleDeepLink(intent)
        } else {
            android.util.Log.i("VT4.Startup", "Restore: Nav-state behouden; graph niet opnieuw gezet.")
        }

        val t3 = SystemClock.elapsedRealtimeNanos()

        // 6) First-frame hook
        binding.root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                binding.root.viewTreeObserver.removeOnPreDrawListener(this)
                try { reportFullyDrawn() } catch (_: Throwable) { /* no-op */ }
                val t4 = SystemClock.elapsedRealtimeNanos()
                android.util.Log.i(
                    "VT4.Startup",
                    "Phases (ms): onCreate->theme=${(t1 - t0) / 1_000_000.0}, " +
                            "theme->inflate=${(t2 - t1) / 1_000_000.0}, " +
                            "inflate->nav=${(t3 - t2) / 1_000_000.0}, " +
                            "nav->firstDraw=${(t4 - t3) / 1_000_000.0}"
                )

                // Vrijgeven van de splash
                contentReady = true

                // 🌡️ Start parallelle warmup (off-main, time-box ~1.2s) — UI blijft vlot
                lifecycleScope.launch {
                    try {
                        withTimeout(1200L) {
                            withContext(Dispatchers.IO) {
                                AliasesRepository.warmup(this@StartActiviteit)
                            }
                        }
                    } catch (_: TimeoutCancellationException) {
                        // te lang bezig → laat rustig verder draaien in latere interacties
                    } catch (_: Throwable) {
                        // preload faalde zachtjes; UI niet blokkeren
                    }
                }
                return true
            }
        })
    }

    /**
     * Wijzig night mode alleen als de *feitelijke* huidige UI-modus (resources.configuration)
     * niet overeenkomt met de gewenste. Zo vermijden we een recreate bij appstart.
     *
     * Default is 'system'.
     */
    private fun applyThemeFromPrefsGuarded() {
        val pref = sharedPrefs.getString(SettingsKeys.KEY_THEME_MODE, SettingsKeys.THEME_SYSTEM)
        val desiredMode = when (pref) {
            SettingsKeys.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            SettingsKeys.THEME_DARK  -> AppCompatDelegate.MODE_NIGHT_YES
            SettingsKeys.THEME_SYSTEM, null, "" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

        val currentNightMask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isCurrentlyNight = currentNightMask == Configuration.UI_MODE_NIGHT_YES

        val changeNeeded = when (desiredMode) {
            AppCompatDelegate.MODE_NIGHT_YES -> !isCurrentlyNight
            AppCompatDelegate.MODE_NIGHT_NO  -> isCurrentlyNight
            else -> AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

        if (changeNeeded) {
            AppCompatDelegate.setDefaultNightMode(desiredMode)
            android.util.Log.i("VT4.Theme", "Night mode changed to $desiredMode (pref='$pref')")
        } else {
            android.util.Log.i("VT4.Theme", "Night mode unchanged (pref='$pref')")
        }
    }

    private fun applyEdgeToEdgeInsets(root: View) {
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
        root.requestApplyInsets()
    }
}

/** Gecentraliseerde instellingen-sleutels. */
object SettingsKeys {
    const val PREFS_NAME = "voicetally_prefs"
    const val KEY_PERMISSIONS_DONE = "permissions_done_once"

    // Thema
    const val KEY_THEME_MODE = "theme_mode" // "dark" | "light" | "system"
    const val THEME_DARK = "dark"
    const val THEME_LIGHT = "light"
    const val THEME_SYSTEM = "system"
}
