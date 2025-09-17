package com.yvesds.voicetally4.utils.codes

import android.content.Context
import android.util.Log
import com.yvesds.voicetally4.utils.io.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.util.Locale
import kotlin.concurrent.Volatile

/**
 * Snelle code/label mapping op basis van lokaal bestand:
 * - Primair: Documents/VoiceTally4/serverdata/codes.json (door app of user geplaatst)
 * - Fallback: assets/trektellen_codes.json (meegebundelde baseline)
 *
 * Optimalisatie:
 * - Binaire cache in **Documents/VoiceTally4/binaries/codes.bin**
 *   We slaan bron-bestandsgrootte + mtime op in de header; alleen heropbouwen als JSON wijzigt.
 *
 * Velden die we nu ondersteunen:
 * - typetelling_trek
 * - wind
 * - neerslag
 *
 * Threading:
 * - Nieuwe `suspend fun ensureLoadedAsync(...)` voor I/O op Dispatchers.IO.
 * - Sync `ensureLoaded(...)` blijft bestaan (drop-in); voert I/O in elk geval op Dispatchers.IO uit.
 */
object CodesIndex {

    private const val TAG = "CodesIndex"

    private const val JSON_FILE_NAME = "codes.json"
    private const val ASSETS_JSON = "trektellen_codes.json"

    private const val BIN_FILE_NAME = "codes.bin"
    private const val BIN_VERSION = 2 // ↑ verhoogd door uitgebreidere header (size+mtime)

    private const val ARRAY_KEY = "json"

    // Interessante velden voor dit scherm
    private val SUPPORTED_FIELDS: Set<String> = setOf(
        "typetelling_trek",
        "wind",
        "neerslag"
    )

    @Volatile
    private var loaded: Boolean = false

    // veld -> (labelLower -> code)
    private val labelToCodePerField: MutableMap<String, Map<String, String>> = mutableMapOf()

    // veld -> (code -> label)
    private val codeToLabelPerField: MutableMap<String, Map<String, String>> = mutableMapOf()

    // veld -> labels gesorteerd (UI)
    private val labelsSortedPerField: MutableMap<String, List<String>> = mutableMapOf()

    private val loadMutex = Mutex()

    // --------------
    // Publiek API
    // --------------

    /**
     * Asynchrone loader. Zorgt dat de index 1x geladen is (lazy).
     * Voert disk I/O op Dispatchers.IO uit.
     */
    suspend fun ensureLoadedAsync(context: Context) {
        if (loaded) return
        loadMutex.withLock {
            if (loaded) return
            withContext(Dispatchers.IO) {
                doLoad(context)
                loaded = true
            }
        }
    }

    /**
     * Sync variant (drop-in). Blokkeert de caller, maar voert I/O steeds op Dispatchers.IO uit.
     * Gebruik bij voorkeur [ensureLoadedAsync] vanuit coroutines.
     */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        runBlocking {
            withContext(Dispatchers.IO) {
                loadMutex.withLock {
                    if (!loaded) {
                        doLoad(context)
                        loaded = true
                    }
                }
            }
        }
    }

    /** Labels voor spinner, gesorteerd. Lege lijst indien onbekend veld of niet geladen. */
    fun getLabels(veld: String): List<String> = labelsSortedPerField[veld] ?: emptyList()

    /** Map UI label → servercode (case-insensitive). Null indien onbekend. */
    fun labelToCode(veld: String, label: String?): String? {
        if (label.isNullOrBlank()) return null
        val map = labelToCodePerField[veld] ?: return null
        return map[label.trim().lowercase(Locale.getDefault())]
    }

    /** Map servercode → UI label. Null indien onbekend. */
    fun codeToLabel(veld: String, code: String?): String? {
        if (code.isNullOrBlank()) return null
        val map = codeToLabelPerField[veld] ?: return null
        return map[code]
    }

    /** Handig om te weten of de index bruikbaar is. */
    fun isReady(): Boolean = loaded && labelsSortedPerField.isNotEmpty()

    /** Optioneel: index ongeldig maken (bv. na bestand-update). */
    fun invalidate() {
        loaded = false
        labelToCodePerField.clear()
        codeToLabelPerField.clear()
        labelsSortedPerField.clear()
    }

    // --------------
    // Intern laden/parsen/cachen
    // --------------

    private fun doLoad(context: Context) {
        try {
            val docsDir: File = StorageUtils.getPublicAppDir(context, "serverdata")
            val jsonFile = File(docsDir, JSON_FILE_NAME)

            // Binaire cache in publieke binaries-map (zodat ook via USB vindbaar)
            val binDir: File = StorageUtils.getPublicAppDir(context, "binaries")
            val binFile = File(binDir, BIN_FILE_NAME)

            val jsonExists = jsonFile.exists()
            val jsonSize = if (jsonExists) jsonFile.length() else -1L
            val jsonMtime = if (jsonExists) jsonFile.lastModified() else -1L

            // 1) Probeer bin-cache als die overeenstemt met bron (size+mtime), of als JSON ontbreekt
            if (binFile.exists()) {
                val loadedOk = loadFromBinary(
                    binFile = binFile,
                    expectedSourceSize = if (jsonExists) jsonSize else null,
                    expectedSourceMtime = if (jsonExists) jsonMtime else null
                )
                if (loadedOk) {
                    // Minimaal loggen: enkel nuttige hint bij ontwikkelen
                    Log.d(TAG, "Codes geladen vanuit bin-cache: ${binFile.absolutePath}")
                    return
                }
            }

            // 2) Lees JSON (primair) of assets (fallback)
            val text: String? = when {
                jsonExists -> {
                    jsonFile.inputStream().buffered().use { ins ->
                        BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).readText()
                    }
                }

                else -> {
                    // Fallback naar assets (meegeleverde baseline)
                    runCatching {
                        context.assets.open(ASSETS_JSON).use { assetIn ->
                            assetIn.reader(Charsets.UTF_8).readText()
                        }
                    }.onFailure {
                        Log.i(TAG, "Geen ${jsonFile.absolutePath} en geen assets/$ASSETS_JSON beschikbaar.")
                    }.getOrNull()
                }
            }

            if (text.isNullOrEmpty()) {
                Log.i(TAG, "Geen codes geladen (leeg).")
                return
            }

            // 3) Parse en bouw index
            parseIntoIndexes(text)

            // 4) Schrijf binaire cache (best effort)
            saveToBinary(
                binFile = binFile,
                sourceSize = if (jsonExists) jsonSize else -1L,
                sourceMtime = if (jsonExists) jsonMtime else -1L
            )
            Log.d(
                TAG,
                "Codes geladen uit ${if (jsonExists) "JSON" else "assets"} en gecached naar: ${binFile.absolutePath}"
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Fout bij laden codes: ${t.message}", t)
            // maps kunnen leeg blijven; aanroeper kan dit controleren met isReady()
        }
    }

    private fun parseIntoIndexes(text: String) {
        labelToCodePerField.clear()
        codeToLabelPerField.clear()
        labelsSortedPerField.clear()

        val root = JSONObject(text)
        val arr: JSONArray = root.optJSONArray(ARRAY_KEY) ?: JSONArray()

        // Tijdelijke opslag per veld om te sorteren
        data class CodeItem(val label: String, val code: String, val sort: Int)
        val tmpByField = HashMap<String, MutableList<CodeItem>>()
        val locale = Locale.getDefault()

        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue

            // Alleen Nederlandstalige labels
            val taalid = o.optString("taalid")
            if (taalid != "1") continue

            val veld = o.optString("veld").trim()
            if (veld !in SUPPORTED_FIELDS) continue

            val label = o.optString("tekst").trim()
            val code = o.optString("waarde", "").trim()
            if (label.isEmpty() || code.isEmpty()) continue

            val sortering = o.optString("sortering").toIntOrNull() ?: Int.MAX_VALUE

            tmpByField.getOrPut(veld) { mutableListOf() }
                .add(CodeItem(label = label, code = code, sort = sortering))
        }

        tmpByField.forEach { (veld, list) ->
            // sorteren op sortering en daarna label
            val sorted = list.sortedWith(compareBy<CodeItem> { it.sort }.thenBy { it.label.lowercase(locale) })
            val labels = ArrayList<String>(sorted.size)
            val l2c = HashMap<String, String>(sorted.size)
            val c2l = HashMap<String, String>(sorted.size)
            for (item in sorted) {
                labels.add(item.label)
                l2c[item.label.lowercase(locale)] = item.code
                c2l[item.code] = item.label
            }
            labelsSortedPerField[veld] = labels
            labelToCodePerField[veld] = l2c
            codeToLabelPerField[veld] = c2l
        }
    }

    /**
     * Binaire schrijf:
     * Header: [version:int][sourceSize:long][sourceMtime:long][fieldCount:int]
     * Body:   { veld:String, count:int, [label:String, code:String] * count } * fieldCount
     */
    private fun saveToBinary(binFile: File, sourceSize: Long, sourceMtime: Long) {
        runCatching {
            binFile.parentFile?.mkdirs()
            DataOutputStream(binFile.outputStream().buffered()).use { out ->
                out.writeInt(BIN_VERSION)
                out.writeLong(sourceSize)
                out.writeLong(sourceMtime)

                out.writeInt(labelsSortedPerField.size)
                val locale = Locale.getDefault()
                for ((veld, labels) in labelsSortedPerField) {
                    out.writeUTF(veld)
                    val l2c = labelToCodePerField[veld] ?: emptyMap()
                    out.writeInt(labels.size)
                    for (label in labels) {
                        out.writeUTF(label)
                        out.writeUTF(l2c[label.lowercase(locale)] ?: "")
                    }
                }
                out.flush()
            }
        }.onFailure {
            Log.w(TAG, "Kon binaire cache niet wegschrijven: ${it.message}")
        }
    }

    /**
     * Binaire lees: inverse van saveToBinary.
     * - Als [expectedSourceSize] en [expectedSourceMtime] niet null zijn, moeten ze exact matchen.
     * - Als ze null zijn (assets-fallback), accepteren we de bin zonder check.
     */
    private fun loadFromBinary(
        binFile: File,
        expectedSourceSize: Long?,
        expectedSourceMtime: Long?
    ): Boolean {
        return try {
            DataInputStream(BufferedInputStream(binFile.inputStream())).use { ins ->
                val ver = ins.readInt()
                if (ver != BIN_VERSION) return false

                val savedSize = ins.readLong()
                val savedMtime = ins.readLong()

                if (expectedSourceSize != null && expectedSourceMtime != null) {
                    if (savedSize != expectedSourceSize || savedMtime != expectedSourceMtime) {
                        return false
                    }
                }
                // Wanneer JSON ontbreekt en we assets gebruik(t)en: accepteer binaire cache altijd.

                labelToCodePerField.clear()
                codeToLabelPerField.clear()
                labelsSortedPerField.clear()

                val fieldCount = ins.readInt()
                repeat(fieldCount) {
                    val veld = ins.readUTF()
                    val count = ins.readInt()

                    val labels = ArrayList<String>(count)
                    val l2c = HashMap<String, String>(count)
                    val c2l = HashMap<String, String>(count)

                    repeat(count) {
                        val label = ins.readUTF()
                        val code = ins.readUTF()
                        if (label.isNotEmpty() && code.isNotEmpty()) {
                            labels.add(label)
                            l2c[label.lowercase(Locale.getDefault())] = code
                            c2l[code] = label
                        }
                    }

                    labelsSortedPerField[veld] = labels
                    labelToCodePerField[veld] = l2c
                    codeToLabelPerField[veld] = c2l
                }
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Binaire cache lezen mislukt: ${t.message}")
            false
        }
    }
}
