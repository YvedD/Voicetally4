package com.yvesds.voicetally4.utils.net

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile

/**
 * Eenvoudige, robuuste opslag van Trektellen-inloggegevens met snelle Basic-Auth header.
 *
 * Ontwerpdoelen:
 * - **Snelle start**: SharedPreferences alleen lezen wanneer nodig, daarna in-memory cache.
 * - **Geen onnodige allocaties**: header éénmaal bouwen en cachen, invalidatie op save/clear.
 * - **Geen main-thread I/O**: sync-API blijft bestaan (SharedPrefs read is cheap),
 *   maar er zijn ook `suspend` varianten die op Dispatchers.IO draaien.
 * - **Geen logging van geheimen**.
 *
 * Publiek API (drop-in compat):
 * - save(context, username, password)
 * - get(context): Pair<String, String>?
 * - clear(context)
 * - getBasicAuthHeader(context): String?
 *
 * Extra (nieuw):
 * - isConfigured(context): Boolean
 * - suspend saveAsync(context, username, password)
 * - suspend clearAsync(context)
 */
object CredentialsStore {

    private const val PREFS = "trektellen_auth"
    private const val KEY_USER = "username"
    private const val KEY_PASS = "password"

    @Volatile private var cachedUser: String? = null
    @Volatile private var cachedPass: String? = null
    @Volatile private var cachedHeader: String? = null

    // --------------------------
    // Publieke API (sync)
    // --------------------------

    /** Slaat user+pass op. Invalidatie en rebuild van de in-memory cache. */
    fun save(context: Context, username: String, password: String) {
        // normaliseer blanks -> trim, maar laat spaties in wachtwoord toe
        val u = username.trim()
        val p = password // wachtwoord niet onbedoeld trimmen
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_USER, u).putString(KEY_PASS, p).apply()

        // cache bijwerken
        cachedUser = u
        cachedPass = p
        cachedHeader = buildHeader(u, p)
    }

    /**
     * Leest user+pass uit cache of SharedPreferences.
     * Returnt null wanneer niet (compleet) geconfigureerd.
     */
    fun get(context: Context): Pair<String, String>? {
        val cu = cachedUser
        val cp = cachedPass
        if (!cu.isNullOrBlank() && !cp.isNullOrBlank()) return cu to cp

        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val u = sp.getString(KEY_USER, null)
        val p = sp.getString(KEY_PASS, null)

        return if (!u.isNullOrBlank() && !p.isNullOrBlank()) {
            cachedUser = u
            cachedPass = p
            cachedHeader = null // forceer rebuild bij eerstvolgende header-aanvraag
            u to p
        } else {
            null
        }
    }

    /** Verwijdert credentials en wist de in-memory cache. */
    fun clear(context: Context) {
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().remove(KEY_USER).remove(KEY_PASS).apply()
        cachedUser = null
        cachedPass = null
        cachedHeader = null
    }

    /** True wanneer er geldige (niet-lege) credentials bewaard zijn. */
    fun isConfigured(context: Context): Boolean = get(context) != null

    /**
     * Voor Basic Auth in HTTP header, bv. "Basic QWxhZGRpbjpPcGVuU2VzYW1l".
     * Returnt null als credentials ontbreken.
     */
    fun getBasicAuthHeader(context: Context): String? {
        // 1) Snelpad: cached header
        val ch = cachedHeader
        if (!ch.isNullOrEmpty()) return ch

        // 2) Credentials ophalen (cache of prefs)
        val creds = get(context) ?: return null

        // 3) Bouwen en cachen
        val header = buildHeader(creds.first, creds.second)
        cachedHeader = header
        return header
    }

    // --------------------------
    // Publieke API (async varianten)
    // --------------------------

    /** I/O-vriendelijke variant van [save]. */
    suspend fun saveAsync(context: Context, username: String, password: String) =
        withContext(Dispatchers.IO) { save(context, username, password) }

    /** I/O-vriendelijke variant van [clear]. */
    suspend fun clearAsync(context: Context) =
        withContext(Dispatchers.IO) { clear(context) }

    // --------------------------
    // Intern
    // --------------------------

    private fun buildHeader(username: String, password: String): String {
        val token = "$username:$password"
        // NO_WRAP om geen CRLF in header te krijgen; padding laten staan (server-compat).
        val b64 = Base64.encodeToString(token.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "Basic $b64"
    }
}
