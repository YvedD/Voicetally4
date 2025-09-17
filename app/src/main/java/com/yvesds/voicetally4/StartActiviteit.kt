package com.yvesds.voicetally4

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import com.yvesds.voicetally4.databinding.ActivityMainBinding
import com.yvesds.voicetally4.ui.core.SetupManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Launcher-activity (edge-to-edge + runtime startDestination):
 * - Hilt injectie voor SharedPreferences (SetupManager gebruikt prefs intern)
 * - Bepaalt startDestination op basis van **setup.isSetupComplete()** (en niet langer een losse vlag)
 * - Robuust ophalen van NavHostFragment (ID: R.id.nav_host uit activity_main.xml)
 * - Géén day/night theme-wissel meer (en dus geen activity recreate bij start)
 */
@AndroidEntryPoint
class StartActiviteit : AppCompatActivity() {

    @Inject lateinit var sharedPrefs: SharedPreferences

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdgeInsets(binding.root)

        // --------- Kies startDestination vóór de eerste render ---------
        val setup = SetupManager(this, sharedPrefs)

        // Belangrijk: ID komt uit activity_main.xml -> android:id="@+id/nav_host"
        val navHost: NavHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host) as? NavHostFragment
            ?: throw IllegalStateException(
                "NavHostFragment niet gevonden. " +
                        "Controleer dat activity_main.xml een FragmentContainerView met id @id/nav_host bevat."
            )

        val navController = navHost.navController
        // Inflate altijd de graph uit resources, zodat we startDestination kunnen overschrijven
        val graph = navController.navInflater.inflate(R.navigation.nav_graph)

        // **Unified logica**: gebruik dezelfde check als in EersteSetupScherm
        val startDest = if (setup.isSetupComplete()) {
            R.id.opstartScherm
        } else {
            R.id.eersteSetupScherm
        }
        if (graph.startDestinationId != startDest) {
            graph.setStartDestination(startDest)
        }
        navController.graph = graph
        // ----------------------------------------------------------------------
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
