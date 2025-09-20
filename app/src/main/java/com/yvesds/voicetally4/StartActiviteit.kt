package com.yvesds.voicetally4

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.fragment.NavHostFragment
import com.yvesds.voicetally4.utils.settings.SettingsKeys
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StartActiviteit : AppCompatActivity() {

    private var holdSplash = true

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { holdSplash }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController

        val graph = navController.navInflater.inflate(R.navigation.nav_graph)

        val prefs = getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val setupDone = prefs.getBoolean(SettingsKeys.KEY_SETUP_DONE, false)

        graph.setStartDestination(if (setupDone) R.id.opstartScherm else R.id.eersteSetupScherm)
        navController.graph = graph

        holdSplash = false
    }
}