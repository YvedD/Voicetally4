package com.yvesds.voicetally4.utils.net

import android.util.Base64
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ApiResponse(
    val ok: Boolean,
    val code: Int,
    val body: String
)

/**
 * HTTP-client voor de Trektellen API.
 * - GET met Basic Auth
 * - POST JSON met Basic Auth
 */
class TrektellenApi {

    private val baseUrl = "https://trektellen.nl/api/"
    private val userAgent = "VoiceTally4/1.0 (Android)"

    private fun buildQuery(params: Map<String, String>): String =
        params.entries.joinToString("&") { (k, v) -> "${k}=${URLEncoder.encode(v, "UTF-8")}" }

    private fun authHeader(username: String, password: String): String {
        val token = Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)
        return "Basic $token"
    }

    private fun readResponse(conn: HttpURLConnection): ApiResponse {
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use(BufferedReader::readText)
            ApiResponse(code in 200..299, code, body)
        } catch (t: Throwable) {
            ApiResponse(false, -1, t.message ?: "error")
        } finally {
            conn.disconnect()
        }
    }

    /** GET /api/codes?language=&versie=  (Basic Auth) */
    fun getCodesBasicAuth(username: String, password: String): ApiResponse {
        val query = mapOf("language" to "dutch", "versie" to "1845")
        val url = URL(baseUrl + "codes?" + buildQuery(query))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 30000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Authorization", authHeader(username, password))
        }
        return readResponse(conn)
    }

    /** POST /api/counts_save  (Basic Auth, JSON body als array-string) */
    fun postCountsSaveBasicAuth(
        username: String,
        password: String,
        jsonArrayString: String
    ): ApiResponse {
        val url = URL(baseUrl + "counts_save")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 30000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Authorization", authHeader(username, password))
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        return try {
            conn.outputStream.use { os ->
                BufferedWriter(OutputStreamWriter(os, Charsets.UTF_8)).use { w ->
                    w.write(jsonArrayString)
                }
            }
            readResponse(conn)
        } catch (t: Throwable) {
            try { conn.disconnect() } catch (_: Throwable) {}
            ApiResponse(false, -1, t.message ?: "error")
        }
    }
}
