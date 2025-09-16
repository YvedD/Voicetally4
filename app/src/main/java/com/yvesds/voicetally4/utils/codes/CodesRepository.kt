package com.yvesds.voicetally4.utils.codes

import android.content.Context
import android.util.Log
import com.yvesds.voicetally4.utils.io.StorageUtils
import com.yvesds.voicetally4.utils.net.TrektellenApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Ophalen en opslaan van Trektellen-codes.
 * Slaat het resultaat op als: Documents/VoiceTally4/serverdata/codes.json
 */
object CodesRepository {

    data class Result(val ok: Boolean, val httpCode: Int, val body: String)

    private const val TAG = "CodesRepository"

    // Zelfde timeouts als netwerklaag
    private const val CONNECT_TIMEOUT_MS = 3_000
    private const val READ_TIMEOUT_MS = 5_500
    private const val UA = "VoiceTally4/1.0 (Android)"

    /**
     * Primair: Basic Auth (via CredentialsStore intern in TrektellenApi).
     */
    suspend fun fetchCodesBasicAuthAndSave(
        context: Context,
        language: String = "dutch",
        versie: String = "1845"
    ): Result = withContext(Dispatchers.IO) {
        val resp = TrektellenApi.getCodesBasicAuthAsync(context, language, versie)
        if (resp.ok) {
            StorageUtils.saveStringToPublicDocumentsAsync(
                context = context,
                subDir = "VoiceTally4/serverdata",
                fileName = "codes.json",
                content = resp.body
            )
            CodesIndex.invalidate()
        }
        Result(resp.ok, resp.httpCode, resp.body)
    }

    /**
     * Fallback: “nood-URL” met query parameters (zonder Basic Auth).
     * Voorziet de parameters zoals door de gebruiker opgegeven.
     * LET OP: deze credentials **niet** loggen.
     */
    suspend fun fetchCodesFallbackByQueryAndSave(
        context: Context,
        language: String = "dutch",
        versie: String = "1845",
        naam: String = "byvesds",
        ww: String = "AtTrektellen1963"
    ): Result = withContext(Dispatchers.IO) {
        val urlStr = buildString {
            append("https://trektellen.nl/api/codes?")
            append("naam="); append(naam)
            append("&ww="); append(ww)
            append("&language="); append(language)
            append("&versie="); append(versie)
        }

        val resp = httpGet(URL(urlStr))
        if (resp.ok) {
            StorageUtils.saveStringToPublicDocumentsAsync(
                context = context,
                subDir = "VoiceTally4/serverdata",
                fileName = "codes.json",
                content = resp.body
            )
            CodesIndex.invalidate()
        }
        resp
    }

    // --- intern ---

    private suspend fun httpGet(url: URL): Result = withContext(Dispatchers.IO) {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", UA)
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            Result(code in 200..299, code, body)
        } catch (t: Throwable) {
            Log.w(TAG, "httpGet fout: ${t.message}")
            Result(false, -1, t.message ?: "network error")
        } finally {
            try { conn.disconnect() } catch (_: Throwable) {}
        }
    }
}
