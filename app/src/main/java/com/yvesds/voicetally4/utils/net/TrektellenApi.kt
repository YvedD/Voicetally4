package com.yvesds.voicetally4.utils.net

import android.content.Context
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Heel lichte API-wrapper voor Trektellen met Basic Auth.
 * - GET /api/codes (indien je die elders nog wil gebruiken)
 * - POST /api/counts_save  (metadata upload)
 */
object TrektellenApi {

    data class SimpleHttpResponse(val ok: Boolean, val httpCode: Int, val body: String)

    private const val BASE = "https://trektellen.nl"
    private const val TIMEOUT_CONNECT = 15000
    private const val TIMEOUT_READ = 20000

    /**
     * GET /api/codes?language=dutch&versie=1845 — met Basic Auth.
     * (Niet automatisch gebruikt door MetadataScherm; enkel voorzien voor later gebruik.)
     */
    fun getCodesBasicAuth(context: Context, language: String = "dutch", versie: String = "1845"): SimpleHttpResponse {
        val auth = CredentialsStore.getBasicAuthHeader(context)
            ?: return SimpleHttpResponse(false, 401, "Geen credentials")
        val url = URL("$BASE/api/codes?language=$language&versie=$versie")

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_CONNECT
            readTimeout = TIMEOUT_READ
            setRequestProperty("Authorization", auth)
        }
        return execute(conn)
    }

    /**
     * POST /api/counts_save met raw JSON-array in de body.
     * Verwacht Basic Auth via CredentialsStore.
     */
    fun postCountsSaveBasicAuth(context: Context, jsonArrayBody: String): SimpleHttpResponse {
        val auth = CredentialsStore.getBasicAuthHeader(context)
            ?: return SimpleHttpResponse(false, 401, "Geen credentials")

        val url = URL("$BASE/api/counts_save")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_CONNECT
            readTimeout = TIMEOUT_READ
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", auth)
        }

        return try {
            conn.outputStream.use { os ->
                BufferedWriter(OutputStreamWriter(os, Charsets.UTF_8)).use { w ->
                    w.write(jsonArrayBody)
                }
            }
            execute(conn)
        } catch (t: Throwable) {
            SimpleHttpResponse(false, -1, t.message ?: "unknown error").also { conn.disconnect() }
        }
    }

    // --- intern ---

    private fun execute(conn: HttpURLConnection): SimpleHttpResponse {
        return try {
            val code = conn.responseCode
            val body = try {
                conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            } catch (_: Throwable) {
                conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
            }
            SimpleHttpResponse(code in 200..299, code, body)
        } finally {
            conn.disconnect()
        }
    }
}
