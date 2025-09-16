package com.yvesds.voicetally4

import android.app.Application
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
class VoiceTallyApp : Application()

/**
 * Qualifier voor de application-brede CoroutineScope.
 * Zo vermijden we verwarring met andere scopes (ViewModel, lifecycle, etc.).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Kleine, lokale Hilt-module om een @ApplicationScope CoroutineScope te voorzien.
 * We plaatsen dit hier (zelfde bestand) zodat je AppModule niet direct moet wijzigen.
 * Gebruik:
 *   @Inject @ApplicationScope lateinit var appScope: CoroutineScope
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
