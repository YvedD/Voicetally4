package com.yvesds.voicetally4.utils.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object TrektellenApi {
    private const val BASE_URL = "https://www.trektellen.org/api"
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun startTelling(
        context: Context,
        payload: JSONObject
    ): JSONObject = withContext(Dispatchers.IO) {
        val (user, pass) = CredentialsStore.getCredentials(context)
        require(!user.isNullOrBlank() && !pass.isNullOrBlank()) { "Geen credentials beschikbaar" }

        val url = "$BASE_URL/counts_save"
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Authorization", CredentialsStore.getBasicAuthHeader(user, pass))
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Serverfout: ${resp.code}")
            val txt = resp.body?.string() ?: throw IllegalStateException("Leeg antwoord")
            return@withContext JSONObject(txt)
        }
    }
}
