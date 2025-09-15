package com.yvesds.voicetally4.utils.codes

import android.content.Context
import android.util.Log
import com.yvesds.voicetally4.utils.io.StorageUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.Locale
import kotlin.concurrent.Volatile

/**
 * Snelle code/label mapping op basis van lokaal bestand:
 *   Documents/VoiceTally4/serverdata/codes.json    (menselijk leesbaar)
 *
 * Optimalisatie:
 *   - We bouwen een binaire cache in app cache-dir: cacheDir/codes_index.bin
 *   - Bij volgende runs laden we die binair (veel sneller) als die nieuwer is dan codes.json
 *
 * Velden die we nu ondersteunen:
 *   - typetelling_trek
 *   - wind
 *   - neerslag
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

    private val labelToCodePerField = mutableMapOf<String, Map<String, String>>()     // veld -> (labelLower -> code)
    private val codeToLabelPerField = mutableMapOf<String, Map<String, String>>()     // veld -> (code -> label)
    private val labelsSortedPerField = mutableMapOf<String, List<String>>()           // veld -> labels gesorteerd

    /** Zorgt dat de index 1x geladen is. */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                val docsDir: File = StorageUtils.getPublicAppDir(context, "serverdata")
                val jsonFile = File(docsDir, JSON_FILE_NAME)
                val binFile = File(context.cacheDir, BIN_FILE_NAME)

                val canUseBin = binFile.exists() && (!jsonFile.exists() || binFile.lastModified() >= jsonFile.lastModified())
                if (canUseBin) {
                    if (loadFromBinary(binFile)) {
                        loaded = true
                        Log.d(TAG, "Codes geladen vanuit bin-cache: ${binFile.absolutePath}")
                        return
                    } else {
                        Log.w(TAG, "Binaire cache corrupt of versie-mismatch, opnieuw opbouwen uit JSON")
                        // fall-through naar JSON parse
                    }
                }

                if (!jsonFile.exists()) {
                    Log.w(TAG, "codes.json niet gevonden in ${jsonFile.absolutePath}")
                    loaded = true // markeer als loaded met lege index; UI valt dan terug op resources
                    return
                }

                val text = jsonFile.inputStream().use { ins ->
                    BufferedReader(InputStreamReader(ins, Charset.forName("UTF-8"))).readText()
                }
                parseIntoIndexes(text)
                // Probeer binaire cache weg te schrijven (best effort)
                saveToBinary(binFile)
                loaded = true
                Log.d(TAG, "Codes geladen uit JSON en gecached naar bin: ${binFile.absolutePath}")
            } catch (t: Throwable) {
                Log.e(TAG, "Fout bij laden codes: ${t.message}", t)
                loaded = true // voorkom herhaald werk binnen één sessie
            }
        }
    }

    private fun parseIntoIndexes(text: String) {
        labelToCodePerField.clear()
        codeToLabelPerField.clear()
        labelsSortedPerField.clear()

        val root = JSONObject(text)
        val arr: JSONArray = root.optJSONArray(ARRAY_KEY) ?: JSONArray()

        val tmpByField = mutableMapOf<String, MutableList<CodeItem>>()

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
            val sorted = list.sortedWith(compareBy<CodeItem> { it.sort }.thenBy { it.label.lowercase(Locale.getDefault()) })
            val labels = sorted.map { it.label }
            val l2c = sorted.associate { it.label.lowercase(Locale.getDefault()) to it.code }
            val c2l = sorted.associate { it.code to it.label }

            labelsSortedPerField[veld] = labels
            labelToCodePerField[veld] = l2c
            codeToLabelPerField[veld] = c2l
        }
    }

    /** Binaire schrijf: [version][fieldCount]{ field,[count]{label,code} } */
    private fun saveToBinary(binFile: File) {
        try {
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
        } catch (t: Throwable) {
            Log.w(TAG, "Kon binaire cache niet wegschrijven: ${t.message}")
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

    private data class CodeItem(val label: String, val code: String, val sort: Int)

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
}
