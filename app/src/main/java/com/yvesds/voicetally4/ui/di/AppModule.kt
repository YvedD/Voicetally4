package com.yvesds.voicetally4.ui.di

import android.content.Context
import android.content.SharedPreferences
import com.yvesds.voicetally4.ui.data.UnifiedAliasStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Single source of truth voor aliassen.
     */
    @Provides
    @Singleton
    fun provideUnifiedAliasStore(): UnifiedAliasStore = UnifiedAliasStore

    /**
     * App-context beschikbaar maken waar nodig.
     */
    @Provides
    @Singleton
    fun provideAppContext(@ApplicationContext ctx: Context): Context = ctx

    /**
     * SharedPreferences — vereist door EersteSetupScherm.
     * Gebruik een expliciete, app-specifieke naam i.p.v. default prefs.
     */
    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext ctx: Context): SharedPreferences {
        return ctx.getSharedPreferences("vt4_prefs", Context.MODE_PRIVATE)
    }
}
