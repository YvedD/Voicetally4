package com.yvesds.voicetally4.ui.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset

/**
 * Leest optioneel: Documents/VoiceTally4/serverdata/codes.json
 *
 * JOUW SCHEMA:
 * {
 *   "json": [
 *     { "veld": "typetelling_trek", "tekst": "alle soorten", "waarde": "all", "sortering": "373", ... },
 *     { "veld": "wind",             "tekst": "N",            "waarde": "n",   "sortering": "10",  ... },
 *     { "veld": "neerslag",         "tekst": "regen",        "waarde": "regen", ... },
 *     ...
 *   ]
 * }
 *
 * We extraheren lijsten voor MetadataScherm:
 *  - typeTelling (uit veld == "typetelling_trek", op "tekst", sortering oplopend)
 *  - windrichting (uit veld == "wind", op "tekst", sortering oplopend)
 *  - neerslag (uit veld == "neerslag", op "tekst", sortering oplopend)
 *
 * Ontbreekt bestand → return null en UI gebruikt resources-fallback.
 */
object CodesRepository {

    private const val TAG = "CodesRepository"
    private const val APP_DIR = "VoiceTally4"
    private const val SUBDIR = "serverdata"
    private const val FILE_NAME = "codes.json"
    private const val REL_PATH = "Documents/$APP_DIR/$SUBDIR"

    data class CodesConfig(
        val typeTelling: List<String>?,
        val windrichting: List<String>?,
        val neerslag: List<String>?,
    )

    suspend fun loadIfPresent(context: Context): CodesConfig? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { loadViaMediaStore(context) }.getOrNull()?.let { return@withContext it }
        }
        runCatching { loadViaFile(context) }.getOrNull()
    }

    // ---------------- intern ----------------

    private fun loadViaMediaStore(context: Context): CodesConfig? {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.DISPLAY_NAME
        )
        val sel = "${MediaStore.Files.FileColumns.RELATIVE_PATH}=? AND ${MediaStore.Files.FileColumns.DISPLAY_NAME}=?"
        val args = arrayOf("$REL_PATH/", FILE_NAME)
        context.contentResolver.query(collection, projection, sel, args, null)?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY, id)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val txt = input.readBytes().toString(Charset.forName("UTF-8"))
                    return parseToConfig(txt)
                }
            }
        }
        return null
    }

    private fun loadViaFile(context: Context): CodesConfig? {
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val f = File(File(docs, APP_DIR), SUBDIR).resolve(FILE_NAME)
        if (!f.exists() || !f.isFile || !f.canRead()) return null
        val txt = f.readText(Charsets.UTF_8)
        return parseToConfig(txt)
    }

    private fun parseToConfig(json: String): CodesConfig? {
        return try {
            val root = JSONObject(json)
            val arr = root.optJSONArray("json") ?: return null

            fun byVeld(veld: String): List<Entry> =
                arr.toEntryList().filter { it.veld.equals(veld, ignoreCase = true) }
                    .sortedBy { it.sortering ?: Int.MAX_VALUE }

            val typeTelling = byVeld("typetelling_trek").map { it.tekst }.distinct().ifEmpty { null }
            val windrichting = byVeld("wind").map { it.tekst }.distinct().ifEmpty { null }
            val neerslag = byVeld("neerslag").map { it.tekst }.distinct().ifEmpty { null }

            CodesConfig(
                typeTelling = typeTelling,
                windrichting = windrichting,
                neerslag = neerslag
            )
        } catch (t: Throwable) {
            Log.w(TAG, "parse failed: ${t.message}")
            null
        }
    }

    // ------------ helpers ------------

    private data class Entry(
        val veld: String,
        val tekst: String,
        val waarde: String?,
        val sortering: Int?
    )

    private fun JSONArray.toEntryList(): List<Entry> {
        val out = ArrayList<Entry>(length())
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            val veld = obj.optString("veld", "")
            val tekst = obj.optString("tekst", "")
            val waarde = if (obj.has("waarde") && !obj.isNull("waarde")) obj.optString("waarde") else null
            val sortering = obj.optString("sortering", "").toIntOrNull()
            if (veld.isNotBlank() && tekst.isNotBlank()) {
                out.add(Entry(veld, tekst, waarde, sortering))
            }
        }
        return out
    }
}
