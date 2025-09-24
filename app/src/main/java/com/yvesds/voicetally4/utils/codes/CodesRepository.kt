package com.yvesds.voicetally4.utils.codes

import android.content.Context
import com.yvesds.voicetally4.utils.io.StorageUtils
import com.yvesds.voicetally4.utils.net.TrektellenApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ophalen en opslaan van Trektellen-codes via Basic Auth.
 *
 * - Doelbestand: Documents/VoiceTally4/serverdata/codes.json
 * - I/O draait op Dispatchers.IO
 */
object CodesRepository {

    data class Result(
        val ok: Boolean,
        val httpCode: Int,
        val body: String
    )

    /**
     * Haalt /api/codes op met Basic Auth (credentials uit CredentialsStore) en
     * schrijft de JSON publiek weg als Documents/VoiceTally4/serverdata/codes.json.
     *
     * @return Result met ok/httpCode/body (body = volledige JSON of foutboodschap).
     */
    suspend fun fetchCodesBasicAuthAndSave(
        context: Context,
        language: String = "dutch",
        versie: String = "1845"
    ): Result = withContext(Dispatchers.IO) {
        // Gebruik de suspend-variant om runBlocking in de deprecated wrapper te vermijden
        val resp = TrektellenApi.getCodesBasicAuthAsync(context, language, versie)

        if (resp.ok) {
            // NB: StorageUtils.saveJsonToPublicDocuments zet altijd onder Documents/VoiceTally4/
            StorageUtils.saveJsonToPublicDocuments(
                context = context,
                relativeSubdir = "serverdata",
                fileName = "codes.json",
                content = resp.body
            )
        }
        Result(resp.ok, resp.httpCode, resp.body)
    }
}