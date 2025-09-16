package com.yvesds.voicetally4.utils.net

import android.content.Context
import android.os.Looper
import androidx.annotation.CheckResult
import com.yvesds.voicetally4.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Lichtgewicht Trektellen API-wrapper met Basic Auth.
 *
 * Belangrijk:
 * - **Nieuwe async API**: gebruik de `suspend` varianten om **niet** op de main thread te blokkeren.
 * - De oude sync-methodes zijn behouden (drop-in), maar gemarkeerd als @Deprecated.
 * - Korte timeouts + nette foutafhandeling.
 */
object TrektellenApi {

    data class SimpleHttpResponse(
        val ok: Boolean,
        val httpCode: Int,
        val body: String
    )

    private const val BASE = "https://trektellen.nl"
    // Kort en scherp voor snappier UX
    private const val TIMEOUT_CONNECT_MS = 3_000
    private const val TIMEOUT_READ_MS = 5_500

    // -----------------------------
    // Publieke SUSPEND-API (aanbevolen)
    // -----------------------------

    /**
     * GET /api/codes?language=...&versie=...
     * Basic Auth via CredentialsStore.
     */
    @CheckResult
    suspend fun getCodesBasicAuthAsync(
        context: Context,
        language: String = "dutch",
        versie: String = "1845"
    ): SimpleHttpResponse = withContext(Dispatchers.IO) {
        val auth = CredentialsStore.getBasicAuthHeader(context)
            ?: return@withContext SimpleHttpResponse(false, 401, "Geen credentials")

        val url = URL("$BASE/api/codes?language=$language&versie=$versie")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_CONNECT_MS
            readTimeout = TIMEOUT_READ_MS
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Authorization", auth)
            setRequestProperty("Accept", "application/json")
        }
        doRequest(conn)
    }

    /**
     * POST /api/counts_save met **raw JSON array** body.
     * Basic Auth via CredentialsStore.
     */
    @CheckResult
    suspend fun postCountsSaveBasicAuthAsync(
        context: Context,
        jsonArrayBody: String
    ): SimpleHttpResponse = withContext(Dispatchers.IO) {
        val auth = CredentialsStore.getBasicAuthHeader(context)
            ?: return@withContext SimpleHttpResponse(false, 401, "Geen credentials")

        val url = URL("$BASE/api/counts_save")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_CONNECT_MS
            readTimeout = TIMEOUT_READ_MS
            doOutput = true
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Authorization", auth)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        try {
            conn.outputStream.use { os ->
                BufferedWriter(OutputStreamWriter(os, Charsets.UTF_8)).use { w ->
                    w.write(jsonArrayBody)
                    w.flush()
                }
            }
        } catch (t: Throwable) {
            // Als schrijven faalt nog netjes disconnecten
            conn.disconnect()
            return@withContext SimpleHttpResponse(false, -1, t.message ?: "write error")
        }

        doRequest(conn)
    }

    // -----------------------------
    // Backwards compatible sync-API (deprecated – vermijd op main thread)
    // -----------------------------

    /**
     * **Deprecated**: gebruik [getCodesBasicAuthAsync]. Deze methode zal blokkeren
     * als je ze op de main thread aanroept. Alleen behouden voor drop-in compat.
     */
    @Deprecated("Gebruik getCodesBasicAuthAsync (suspend) om niet te blokkeren op main.")
    @JvmStatic
    fun getCodesBasicAuth(
        context: Context,
        language: String = "dutch",
        versie: String = "1845"
    ): SimpleHttpResponse {
        return runBlockingOnIoIfMain {
            runBlocking { getCodesBasicAuthAsync(context, language, versie) }
        }
    }

    /**
     * **Deprecated**: gebruik [postCountsSaveBasicAuthAsync]. Deze methode zal blokkeren
     * als je ze op de main thread aanroept. Alleen behouden voor drop-in compat.
     */
    @Deprecated("Gebruik postCountsSaveBasicAuthAsync (suspend) om niet te blokkeren op main.")
    @JvmStatic
    fun postCountsSaveBasicAuth(
        context: Context,
        jsonArrayBody: String
    ): SimpleHttpResponse {
        return runBlockingOnIoIfMain {
            runBlocking { postCountsSaveBasicAuthAsync(context, jsonArrayBody) }
        }
    }

    // -----------------------------
    // Intern
    // -----------------------------

    private inline fun <T> runBlockingOnIoIfMain(block: () -> T): T {
        // Houd UI responsief: voer het werk in ieder geval buiten main uit.
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            runBlocking(Dispatchers.IO) { block() }
        } else {
            block()
        }
    }

    private suspend fun doRequest(conn: HttpURLConnection): SimpleHttpResponse {
        // Zorg dat we kunnen annuleren: bij cancel -> disconnect()
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation {
                // Probeer de verbinding te verbreken om I/O te onderbreken
                try {
                    conn.disconnect()
                } catch (_: Throwable) {
                }
            }

            try {
                val code = conn.responseCode // triggert de request
                val body = readBody(conn)
                cont.resume(
                    SimpleHttpResponse(
                        ok = code in 200..299,
                        httpCode = code,
                        body = body
                    )
                )
            } catch (ce: CancellationException) {
                // Doorsturen als nette cancel
                conn.disconnect()
                throw ce
            } catch (t: Throwable) {
                cont.resume(
                    SimpleHttpResponse(false, -1, t.message ?: "network error")
                )
            } finally {
                try {
                    conn.disconnect()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun readBody(conn: HttpURLConnection): String {
        val contentType = (conn.contentType ?: "").lowercase(Locale.ROOT)
        val charset = extractCharset(contentType) ?: Charsets.UTF_8
        val stream: InputStream? = try {
            if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        } catch (_: Throwable) {
            conn.errorStream
        }
        if (stream == null) return ""
        return stream.buffered().use { ins ->
            InputStreamReader(ins, charset).use { reader ->
                reader.readText()
            }
        }
    }

    private fun extractCharset(contentType: String): Charset? {
        // bijv. "application/json; charset=utf-8"
        val parts = contentType.split(';')
        for (p in parts) {
            val kv = p.trim().split('=')
            if (kv.size == 2 && kv[0].trim() == "charset") {
                return runCatching { Charset.forName(kv[1].trim()) }.getOrNull()
            }
        }
        return null
    }
}
