package com.yvesds.voicetally4.ui.di

import android.content.Context
import android.content.SharedPreferences
import com.yvesds.voicetally4.ui.core.SetupManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Centrale DI-module.
 * Let op: geen afhankelijkheid meer van SettingsKeys of StartActiviteit voor de prefs-naam.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** Één vaste naam voor app-brede instellingen. */
    private const val PREFS_NAME: String = "voicetally_prefs"

    @Provides
    @Singleton
    fun provideSharedPrefs(@ApplicationContext ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideSetupManager(
        @ApplicationContext ctx: Context,
        prefs: SharedPreferences
    ): SetupManager = SetupManager(ctx, prefs)
}
