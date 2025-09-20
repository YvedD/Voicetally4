package com.yvesds.voicetally4

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@HiltAndroidApp
class VoiceTallyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Forceer één thema (donker) zodat er geen DayNight-recreate/flits kan optreden
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}

/**
 * Qualifier voor de application-brede CoroutineScope.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Hilt-module die een @ApplicationScope CoroutineScope voorziet.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppCoroutineModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        // SupervisorJob zodat één falende child niet de hele scope cancelt
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
