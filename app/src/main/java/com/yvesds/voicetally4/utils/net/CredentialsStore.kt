package com.yvesds.voicetally4.utils.net

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import android.util.Base64

object CredentialsStore {
    private const val PREFS_FILE = "vt4_secure_prefs"
    private const val KEY_USER = "username"
    private const val KEY_PASS = "password"

    private fun getPrefs(context: Context) =
        EncryptedSharedPreferences.create(
            PREFS_FILE,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun saveCredentials(context: Context, username: String, password: String) {
        getPrefs(context).edit().apply {
            putString(KEY_USER, username)
            putString(KEY_PASS, password)
            apply()
        }
    }

    fun getCredentials(context: Context): Pair<String?, String?> {
        val prefs = getPrefs(context)
        return prefs.getString(KEY_USER, null) to prefs.getString(KEY_PASS, null)
    }

    fun hasCredentials(context: Context): Boolean {
        val (u, p) = getCredentials(context)
        return !u.isNullOrBlank() && !p.isNullOrBlank()
    }

    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    // 🔹 Toegevoegd zodat TrektellenApi kan compileren
    fun getBasicAuthHeader(username: String, password: String): String {
        val creds = "$username:$password"
        val base64 = Base64.encodeToString(creds.toByteArray(), Base64.NO_WRAP)
        return "Basic $base64"
    }
}
