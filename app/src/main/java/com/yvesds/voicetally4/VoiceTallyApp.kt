package com.yvesds.voicetally4

import android.app.Application
import com.yvesds.voicetally4.ui.data.UnifiedAliasStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class VoiceTallyApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            UnifiedAliasStore.preload(this@VoiceTallyApp)
        }
    }
}
