package com.yvesds.voicetally4.ui.di

import android.content.Context
import android.content.SharedPreferences
import com.yvesds.voicetally4.StartActiviteit
import com.yvesds.voicetally4.ui.core.SetupManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSharedPrefs(@ApplicationContext ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(StartActiviteit.PREFS_NAME, Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideSetupManager(
        @ApplicationContext ctx: Context,
        prefs: SharedPreferences
    ): SetupManager = SetupManager(ctx, prefs)
}
