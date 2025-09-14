package com.yvesds.voicetally4.utils.codes

import android.content.Context
import android.net.Uri
import com.yvesds.voicetally4.utils.io.StorageUtils
import com.yvesds.voicetally4.utils.net.ApiResponse
import com.yvesds.voicetally4.utils.net.CredentialsStore
import com.yvesds.voicetally4.utils.net.TrektellenApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CodesSaveResult(
    val response: ApiResponse,
    val publicUri: Uri?,               // Android 10+: echte Uri, legacy: null
    val publicDisplayPath: String      // "Documents/VoiceTally4/serverdata/codes.json"
)

/**
 * Haalt de codes via Basic Auth op en schrijft publiek naar:
 *   Documents/VoiceTally4/serverdata/codes.json
 * Er wordt niets intern opgeslagen.
 */
class CodesRepository(
    private val context: Context,
    private val creds: CredentialsStore
) {
    private val PUBLIC_REL_DIR = "VoiceTally4/serverdata"
    private val PUBLIC_FILE = "codes.json"
    private val PUBLIC_DISPLAY_PATH = "Documents/$PUBLIC_REL_DIR/$PUBLIC_FILE"

    /**
     * Download via Basic Auth en schrijf publiek weg.
     * @return CodesSaveResult (inclusief volledige body in response.body).
     */
    suspend fun fetchAndSavePublic(): CodesSaveResult = withContext(Dispatchers.IO) {
        val (u, p) = creds.get()
        val apiResp = if (u.isNullOrBlank() || p.isNullOrBlank()) {
            ApiResponse(false, 401, """{"message":"missing credentials"}""")
        } else {
            TrektellenApi().getCodesBasicAuth(u, p)
        }

        val uri = StorageUtils.saveJsonToPublicDocuments(
            context = context,
            relativeSubdir = PUBLIC_REL_DIR,
            fileName = PUBLIC_FILE,
            content = apiResp.body
        )

        CodesSaveResult(
            response = apiResp,
            publicUri = uri,
            publicDisplayPath = PUBLIC_DISPLAY_PATH
        )
    }
}
