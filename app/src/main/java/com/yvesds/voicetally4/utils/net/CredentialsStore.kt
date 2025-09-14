package com.yvesds.voicetally4.utils.net

import android.content.Context
import android.content.SharedPreferences

/**
 * Eenvoudige opslag van credentials in privé SharedPreferences.
 * (Voor productie zou je EncryptedSharedPreferences kunnen overwegen.)
 */
class CredentialsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("trektellen_creds", Context.MODE_PRIVATE)

    fun save(username: String, password: String) {
        prefs.edit().putString(KEY_USER, username).putString(KEY_PASS, password).apply()
    }

    fun get(): Pair<String?, String?> {
        return prefs.getString(KEY_USER, null) to prefs.getString(KEY_PASS, null)
    }

    fun hasCredentials(): Boolean {
        val (u, p) = get()
        return !u.isNullOrBlank() && !p.isNullOrBlank()
    }

    companion object {
        private const val KEY_USER = "user"
        private const val KEY_PASS = "pass"
    }
}
