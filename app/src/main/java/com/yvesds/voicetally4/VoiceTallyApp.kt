package com.yvesds.voicetally4

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import com.yvesds.voicetally4.ui.data.UnifiedAliasStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class VoiceTallyApp : Application() {

    // App-brede scope voor niet-blokkerende initialisaties
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // In debug: vang synchrone disk/netwerk op de main thread (zonder BuildConfig)
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            StrictMode.setThreadPolicy(
                ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }

        // Warm-up zonder main-thread blokkering
        appScope.launch {
            UnifiedAliasStore.preload(this@VoiceTallyApp)
        }
    }
}
