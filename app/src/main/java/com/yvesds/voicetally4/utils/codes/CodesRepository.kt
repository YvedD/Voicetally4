package com.yvesds.voicetally4.utils.codes

import android.content.Context
import android.util.Log
import com.yvesds.voicetally4.utils.io.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.Charset
import java.util.Locale

/**
 * CodesRepository
 *
 * Doel:
 * - Kotlinx.serialization parsing van codes.json met generieke velden (bv. "geslacht", "leeftijd", "kleed", "locatie", "height", "trektype", "teltype", ...).
 * - Case-insensitieve label→code lookup.
 * - Primary bron: Documents/VoiceTally4/serverdata/codes.json
 * - Fallback: assets/trektellen_codes.json
 *
 * JSON-vorm:
 * {
 *   "json":[
 *     {"taalid":"1","veld":"geslacht","tekst":"man","waarde":"M","sortering":"10"},
 *     ...
 *   ]
 * }
 *
 * We filteren op taalid == "1" (NL). Voor andere talen kan later uitgebreid worden.
 */
object CodesRepository {

    private const val TAG = "CodesRepository"
    private const val JSON_ARRAY_KEY = "json"
    private const val ASSETS_FILE = "trektellen_codes.json"
    private const val SERVER_FILE = "codes.json"

    @Serializable
    private data class CodesRoot(
        @SerialName(JSON_ARRAY_KEY) val json: List<CodeRow> = emptyList()
    )

    @Serializable
    private data class CodeRow(
        @SerialName("taalid") val taalid: String = "",
        @SerialName("veld") val veld: String = "",
        @SerialName("tekst") val tekst: String = "",
        @SerialName("waarde") val waarde: String = "",
        @SerialName("sortering") val sortering: String? = null
    )

    // veld -> labelLower -> code
    private val labelToCode: MutableMap<String, Map<String, String>> = mutableMapOf()
    // veld -> code -> label
    private val codeToLabel: MutableMap<String, Map<String, String>> = mutableMapOf()
    private val loadMutex = Mutex()
    @Volatile private var loaded = false

    private val json by lazy {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    suspend fun ensureLoadedAsync(context: Context) {
        if (loaded) return
        loadMutex.withLock {
            if (loaded) return
            withContext(Dispatchers.IO) { doLoad(context) }
            loaded = true
        }
    }

    fun ensureLoaded(context: Context) {
        if (loaded) return
        runBlocking {
            ensureLoadedAsync(context)
        }
    }

    fun invalidate() {
        loaded = false
        labelToCode.clear()
        codeToLabel.clear()
    }

    /** Geef servercode voor veld+label (case-insensitief). Null indien onbekend. */
    fun labelToCode(field: String, label: String?): String? {
        val key = field.trim().lowercase(Locale.getDefault())
        val map = labelToCode[key] ?: return null
        val lbl = label?.trim()?.lowercase(Locale.getDefault()) ?: return null
        return map[lbl]
    }

    /** Geef UI-label voor veld+code. */
    fun codeToLabel(field: String, code: String?): String? {
        val key = field.trim().lowercase(Locale.getDefault())
        val map = codeToLabel[key] ?: return null
        val c = code?.trim() ?: return null
        return map[c]
    }

    /** Labels in UI-volgorde (gesorteerd op sortering, dan alfabetisch). */
    fun labels(field: String): List<String> {
        val key = field.trim().lowercase(Locale.getDefault())
        val inv = codeToLabel[key] ?: return emptyList()
        // sorteer labels alfabetisch; sortering is al toegepast tijdens laden
        return inv.values.toList().sortedBy { it.lowercase(Locale.getDefault()) }
    }

    // ------------------ intern ------------------

    private fun doLoad(context: Context) {
        val docsDir = StorageUtils.getPublicAppDir(context, "serverdata")
        val serverFile = File(docsDir, SERVER_FILE)

        val text: String? = if (serverFile.exists()) {
            runCatching { serverFile.readText(Charset.forName("UTF-8")) }
                .onFailure { Log.w(TAG, "Lezen serverdata/codes.json mislukt: ${it.message}") }
                .getOrNull()
        } else {
            runCatching { context.assets.open(ASSETS_FILE).bufferedReader(Charsets.UTF_8).use { it.readText() } }
                .onFailure { Log.i(TAG, "Geen assets/$ASSETS_FILE beschikbaar") }
                .getOrNull()
        }

        if (text.isNullOrBlank()) {
            Log.i(TAG, "Geen codes geladen (bron leeg).")
            return
        }

        val root = runCatching { json.decodeFromString(CodesRoot.serializer(), text) }
            .onFailure { Log.e(TAG, "JSON parse fout: ${it.message}") }
            .getOrNull()
            ?: return

        val nlRows = root.json.filter { it.taalid == "1" && it.veld.isNotBlank() && it.tekst.isNotBlank() && it.waarde.isNotBlank() }

        // groepeer per veld, sorteer op sortering (indien cijfer), vervolgens label
        val byField = nlRows.groupBy { it.veld.trim().lowercase(Locale.getDefault()) }
        val tmpLabelToCode = mutableMapOf<String, Map<String, String>>()
        val tmpCodeToLabel = mutableMapOf<String, Map<String, String>>()

        for ((field, rows) in byField) {
            val sorted = rows.sortedWith(
                compareBy<CodeRow>({ it.sortering?.toIntOrNull() ?: Int.MAX_VALUE })
                    .thenBy { it.tekst.lowercase(Locale.getDefault()) }
            )
            val l2c = LinkedHashMap<String, String>(sorted.size)
            val c2l = LinkedHashMap<String, String>(sorted.size)
            for (r in sorted) {
                val lblLower = r.tekst.trim().lowercase(Locale.getDefault())
                l2c[lblLower] = r.waarde.trim()
                c2l[r.waarde.trim()] = r.tekst.trim()
            }
            tmpLabelToCode[field] = l2c
            tmpCodeToLabel[field] = c2l
        }

        labelToCode.clear()
        labelToCode.putAll(tmpLabelToCode)
        codeToLabel.clear()
        codeToLabel.putAll(tmpCodeToLabel)

        Log.d(TAG, "Codes geladen voor velden: ${labelToCode.keys}")
    }
}
