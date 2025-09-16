package com.yvesds.voicetally4.utils.codes

import android.content.Context
import android.os.Looper
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
 * Documents/VoiceTally4/serverdata/codes.json (menselijk leesbaar)
 *
 * Optimalisatie:
 * - Binaire cache in app cache-dir: cacheDir/codes_index.bin
 * - Bij volgende runs laden we die binair (veel sneller) als die nieuwer is dan codes.json
 *
 * Velden die we nu ondersteunen:
 * - typetelling_trek
 * - wind
 * - neerslag
 *
 * Threading:
 * - **Nieuwe** `suspend fun ensureLoadedAsync(...)` voor I/O op Dispatchers.IO.
 * - Oude sync `ensureLoaded(...)` behouden (drop-in); die blokkeert indien op main aangeroepen.
 */
object CodesIndex {

    private const val TAG = "CodesIndex"

    private const val JSON_FILE_NAME = "codes.json"
    private const val BIN_FILE_NAME = "codes_index.bin"
    private const val BIN_VERSION = 1
    private const val ARRAY_KEY = "json"

    // Interessante velden voor dit scherm
    private val SUPPORTED_FIELDS = setOf(
        "typetelling_trek",
        "wind",
        "neerslag"
    )

    @Volatile
    private var loaded = false

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
     * Nieuwe, asynchrone loader. Zorgt dat de index 1x geladen is (lazy).
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
     * **Deprecated**: gebruik [ensureLoadedAsync]. Alleen behouden voor drop-in.
     * Blokkeert wanneer op de main thread aangeroepen.
     */
    @Deprecated("Gebruik ensureLoadedAsync (suspend) om niet te blokkeren op main.")
    fun ensureLoaded(context: Context) {
        if (loaded) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runBlocking(Dispatchers.IO) { doLoad(context) }
            loaded = true
        } else {
            doLoad(context)
            loaded = true
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
            val binFile = File(context.cacheDir, BIN_FILE_NAME)

            val canUseBin = binFile.exists() && (!jsonFile.exists() || binFile.lastModified() >= jsonFile.lastModified())

            if (canUseBin && loadFromBinary(binFile)) {
                Log.d(TAG, "Codes geladen vanuit bin-cache: ${binFile.absolutePath}")
                return
            }

            if (!jsonFile.exists()) {
                Log.w(TAG, "codes.json niet gevonden in ${jsonFile.absolutePath}")
                // markeer als loaded met lege index; UI valt dan terug op resources
                return
            }

            val text = jsonFile.inputStream().buffered().use { ins ->
                BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).readText()
            }

            parseIntoIndexes(text)

            // Probeer binaire cache weg te schrijven (best effort)
            saveToBinary(binFile)

            Log.d(TAG, "Codes geladen uit JSON en gecached naar bin: ${binFile.absolutePath}")
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
            val taalid = o.optString("taalid")
            if (taalid != "1") continue // enkel NL

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

    /** Binaire schrijf: [version][fieldCount]{ field,[count]{label,code} } */
    private fun saveToBinary(binFile: File) {
        runCatching {
            DataOutputStream(binFile.outputStream().buffered()).use { out ->
                out.writeInt(BIN_VERSION)
                out.writeInt(labelsSortedPerField.size)
                for ((veld, labels) in labelsSortedPerField) {
                    out.writeUTF(veld)
                    val l2c = labelToCodePerField[veld] ?: emptyMap()
                    out.writeInt(labels.size)
                    for (label in labels) {
                        out.writeUTF(label)
                        out.writeUTF(l2c[label.lowercase(Locale.getDefault())] ?: "")
                    }
                }
                out.flush()
            }
        }.onFailure {
            Log.w(TAG, "Kon binaire cache niet wegschrijven: ${it.message}")
        }
    }

    /** Binaire lees: inverse van saveToBinary */
    private fun loadFromBinary(binFile: File): Boolean {
        return try {
            DataInputStream(BufferedInputStream(binFile.inputStream())).use { ins ->
                val ver = ins.readInt()
                if (ver != BIN_VERSION) return false

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
