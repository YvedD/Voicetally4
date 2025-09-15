package com.yvesds.voicetally4.utils.net

import android.content.Context
import android.util.Base64

/**
 * Eenvoudige opslag/lezing van Trektellen inloggegevens.
 * We bewaren ze in SharedPreferences en kunnen meteen een Basic-Auth header leveren.
 */
object CredentialsStore {

    private const val PREFS = "trektellen_auth"
    private const val KEY_USER = "username"
    private const val KEY_PASS = "password"

    fun save(context: Context, username: String, password: String) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_USER, username).putString(KEY_PASS, password).apply()
    }

    fun get(context: Context): Pair<String, String>? {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val u = sp.getString(KEY_USER, null)
        val p = sp.getString(KEY_PASS, null)
        return if (!u.isNullOrBlank() && !p.isNullOrBlank()) u to p else null
    }

    fun clear(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().clear().apply()
    }

    /** Voor Basic Auth in HTTP header, bv. "Basic QWxhZGRpbjpPcGVuU2VzYW1l". */
    fun getBasicAuthHeader(context: Context): String? {
        val creds = get(context) ?: return null
        val token = "${creds.first}:${creds.second}"
        val b64 = Base64.encodeToString(token.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "Basic $b64"
    }
}
