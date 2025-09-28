package com.yvesds.voicetally4

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StartActiviteit : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash en switch naar hoofdthema (altijd donker)
        installSplashScreen()
        setTheme(R.style.Theme_VoiceTally4)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Belangrijk: dit laadt jouw NavHost + nav_graph uit XML
        setContentView(R.layout.activity_main)
    }
}
