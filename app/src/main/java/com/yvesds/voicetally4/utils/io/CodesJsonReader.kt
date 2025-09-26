package com.yvesds.voicetally4.utils.io

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.Locale

/**
 * Leest strikt lowercase pad via jouw gepersisteerde SAF-root:
 *   VoiceTally4/ -> serverdata/ -> codes.json
 *
 * Eigenschappen:
 * - kotlinx.serialization (lenient + ignoreUnknownKeys)
 * - I/O op Dispatchers.IO
 * - UTF-8 BOM strip
 * - Compact & pretty JSON compatibel
 *
 * Publieke API:
 *  - suspend fun load(context: Context): Boolean
 *  - fun getItems(field: String): List<Entry>
 *  - fun getLabels(field: String): List<String>
 *  - fun debugSummary(field: String): String
 *
 * Mapping: veld -> List<Entry(tekst, waarde, sorteringInt)>, gesorteerd op sortering (numeriek) en vervolgens tekst (A→Z).
 */
object CodesJsonReader {

    private const val DIR_SERVERDATA = "serverdata"
    private const val FILE_CODES = "codes.json"

    // Publiek model voor UI
    data class Entry(
        val tekst: String,
        val waarde: String,
        val sortering: Int
    )

    // Wire-format (exacte servervelden, permissief met nulls)
    @Serializable
    private data class CodesEnvelope(
        @SerialName("json") val json: List<CodesItem> = emptyList()
    )

    @Serializable
    private data class CodesItem(
        @SerialName("tekstkey") val tekstkey: String? = null,
        @SerialName("taalid") val taalid: String? = null,
        @SerialName("tekst") val tekst: String? = null,
        @SerialName("sortering") val sortering: String? = null,
        @SerialName("veld") val veld: String? = null,
        @SerialName("waarde") val waarde: String? = null
    )

    private val json by lazy {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    @Volatile
    private var fieldMap: Map<String, List<Entry>> = emptyMap()

    /**
     * Laadt codes.json via jouw SAF-root. Retourneert true bij succes (≥1 item).
     * I/O gebeurt op Dispatchers.IO.
     */
    suspend fun load(context: Context): Boolean = withContext(Dispatchers.IO) {
        val input = openViaSaf(context) ?: return@withContext false
        input.use { raw ->
            val bytes = raw.readBytes()
            val clean = bytes.stripUtf8BomIfPresent()
            val text = clean.toString(Charsets.UTF_8)

            val envelope = runCatching { json.decodeFromString<CodesEnvelope>(text) }
                .getOrElse { return@withContext false }

            if (envelope.json.isEmpty()) {
                fieldMap = emptyMap()
                return@withContext false
            }

            // Map → group → sort (sortering numeriek, dan tekst A→Z)
            val grouped = envelope.json
                .asSequence()
                .filter { it.veld != null && it.tekst != null } // minimaal nodig voor UI
                .groupBy { it.veld!!.lowercase(Locale.ROOT) }
                .mapValues { (_, list) ->
                    list.map { item ->
                        Entry(
                            tekst = item.tekst ?: "",
                            waarde = item.waarde ?: "", // server kan null sturen; we maken die leeg
                            sortering = item.sortering?.toIntOrNull() ?: Int.MAX_VALUE
                        )
                    }.sortedWith(compareBy<Entry> { it.sortering }.thenBy { it.tekst.lowercase(Locale.ROOT) })
                }
                .filterValues { it.isNotEmpty() }

            fieldMap = grouped
            return@withContext grouped.isNotEmpty()
        }
    }

    /** Haalt de entries voor een bepaald veld op (leeg bij geen data). */
    fun getItems(field: String): List<Entry> =
        fieldMap[field.lowercase(Locale.ROOT)] ?: emptyList()

    /** Alleen labels (tekst) voor spinner-adapters. */
    fun getLabels(field: String): List<String> =
        getItems(field).map { it.tekst }

    /** Debug-hulp: korte samenvatting van het geselecteerde veld. */
    fun debugSummary(field: String): String {
        val items = getItems(field)
        if (items.isEmpty()) return "[$field] (0 items)"
        val head = items.take(3).joinToString { "${it.sortering}:${it.tekst}(${it.waarde})" }
        val more = if (items.size > 3) " … +${items.size - 3} meer" else ""
        return "[$field] ${items.size} items → $head$more"
    }

    // ---- private helpers ----

    private fun ByteArray.stripUtf8BomIfPresent(): ByteArray {
        // UTF-8 BOM: EF BB BF
        return if (size >= 3 && this[0] == 0xEF.toByte() && this[1] == 0xBB.toByte() && this[2] == 0xBF.toByte()) {
            copyOfRange(3, size)
        } else this
    }

    /**
     * Strikt via jouw gepersisteerde SAF-tree onder VoiceTally4:
     * VoiceTally4/ → serverdata/ → codes.json
     */
    private fun openViaSaf(context: Context): InputStream? {
        val doc = try {
            DocumentsAccess.resolveRelativeFile(context, "$DIR_SERVERDATA/$FILE_CODES")
        } catch (_: Throwable) {
            null
        }
        return doc?.let { context.contentResolver.openInputStream(it.uri) }
    }
}
