package com.yvesds.voicetally4.ui.tally

import android.content.Context
import com.yvesds.voicetally4.utils.io.StorageUtils
import com.yvesds.voicetally4.utils.settings.SettingsKeys
import com.yvesds.voicetally4.utils.text.NumberWordsNL
import com.yvesds.voicetally4.utils.text.TextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.charset.Charset

/**
 * Strikte parser: <alias> [<aantal>] ; meerdere paren gescheiden door '-'.
 * - Alias lookup via globale index (binaire cache indien aanwezig; anders CSV).
 * - Default aantal = 1 als ontbreekt.
 * - Sliding window (tot 6 tokens, langste eerst) per segment.
 */
class SpeechParser(private val appContext: Context) {

    data class SpeciesMeta(val id: String, val displayName: String)
    data class Match(val speciesId: String, val displayName: String, val amount: Int)
    data class ParseResult(
        val active: List<Match>,
        val inactive: List<Match>
    )

    // Actieve set bepaalt of een match direct geboekt wordt of via voorstel loopt.
    suspend fun parseOneUtterance(
        rawInput: String,
        activeSpecies: Set<String>
    ): ParseResult = withContext(Dispatchers.Default) {
        val normalized = TextNormalizer.normalizeBasic(rawInput)
        val segments = normalized.split("-").map { it.trim() }.filter { it.isNotEmpty() }

        val globalIndex = AliasIndex.get(appContext)

        val actives = mutableListOf<Match>()
        val inactives = mutableListOf<Match>()

        for (seg in segments) {
            val tokens = seg.split(" ").filter { it.isNotBlank() }
            if (tokens.isEmpty()) continue

            val (aliasSpan, number) = findAliasAndNumber(tokens, globalIndex)
                ?: continue

            val aliasPhrase = aliasSpan.joinToString(" ")
            val meta = globalIndex[aliasPhrase] ?: continue
            val amount = number ?: 1

            val m = Match(meta.id, meta.displayName, amount)
            if (activeSpecies.contains(meta.id)) actives += m else inactives += m
        }

        ParseResult(active = actives, inactive = inactives)
    }

    private fun findAliasAndNumber(
        tokens: List<String>,
        index: Map<String, SpeciesMeta>
    ): Pair<List<String>, Int?>? {
        // Sliding window: 6 -> 1
        val maxWin = 6
        var i = 0
        while (i < tokens.size) {
            val remain = tokens.size - i
            val winMax = minOf(maxWin, remain)
            var matched: List<String>? = null
            var matchedLen = 0
            // langste eerst
            for (w in winMax downTo 1) {
                val slice = tokens.subList(i, i + w)
                val phrase = slice.joinToString(" ")
                if (index.containsKey(phrase)) {
                    matched = slice
                    matchedLen = w
                    break
                }
            }
            if (matched != null) {
                // Check of er direct rechts een aantal staat (digits of NL woord)
                val amount = if (i + matchedLen < tokens.size) {
                    parseAmount(tokens[i + matchedLen])
                } else null
                return matched to amount
            }
            i++
        }
        return null
    }

    private fun parseAmount(token: String): Int? {
        // digits of NL getalwoord
        return token.toIntOrNull() ?: NumberWordsNL.parse(token)
    }

    /**
     * Lazy, thread-safe globale alias-index.
     * Probeert eerst binaire caches onder /binaries, valt terug op CSV in /assets.
     */
    private object AliasIndex {
        @Volatile
        private var cached: Map<String, SpeciesMeta>? = null

        suspend fun get(context: Context): Map<String, SpeciesMeta> {
            cached?.let { return it }
            return withContext(Dispatchers.IO) {
                cached ?: load(context).also { cached = it }
            }
        }

        private fun load(context: Context): Map<String, SpeciesMeta> {
            // 1) Probeer binaries
            val binDir = StorageUtils.getPublicAppDir(context, SettingsKeys.DIR_BINARIES)
            val binA = File(binDir, SettingsKeys.FILE_ALIASES_BIN_A)
            val binB = File(binDir, SettingsKeys.FILE_ALIASES_BIN_B)

            val fromBin = when {
                binA.exists() -> tryLoadBin(binA)
                binB.exists() -> tryLoadBin(binB)
                else -> null
            }
            if (fromBin != null) return fromBin

            // 2) CSV uit assets-folder in Documents
            val csvDir = StorageUtils.getPublicAppDir(context, SettingsKeys.DIR_ASSETS)
            val csv = File(csvDir, SettingsKeys.FILE_ALIAS_CSV)
            if (!csv.exists()) return emptyMap()

            val map = parseCsvLoose(csv)
            // optioneel: bewaar als bin voor volgende keer
            saveBin(binDir, SettingsKeys.FILE_ALIASES_BIN_A, map)
            return map
        }

        private fun tryLoadBin(file: File): Map<String, SpeciesMeta>? = try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { di ->
                val magic = di.readUTF()
                if (magic != "VT4ALIAS1") return null
                val count = di.readInt()
                val out = HashMap<String, SpeciesMeta>(count * 2)
                repeat(count) {
                    val alias = di.readUTF()
                    val id = di.readUTF()
                    val name = di.readUTF()
                    out[alias] = SpeciesMeta(id, name)
                }
                out
            }
        } catch (_: Throwable) {
            null
        }

        private fun saveBin(dir: File, fileName: String, map: Map<String, SpeciesMeta>) {
            try {
                if (!dir.exists()) dir.mkdirs()
                val f = File(dir, fileName)
                DataOutputStream(BufferedOutputStream(FileOutputStream(f))).use { dos ->
                    dos.writeUTF("VT4ALIAS1")
                    dos.writeInt(map.size)
                    for ((alias, meta) in map) {
                        dos.writeUTF(alias)
                        dos.writeUTF(meta.id)
                        dos.writeUTF(meta.displayName)
                    }
                    dos.flush()
                }
            } catch (_: Throwable) {
                // stil falen; CSV blijft de bron
            }
        }

        /**
         * Losse CSV lezer: accepteert ';' of ','.
         * Verwacht per regel minstens: alias, soortId, displayName (volgorde tolerant).
         * Probeert numerieke kolom als id te detecteren; overige strings zijn alias & display.
         */
        private fun parseCsvLoose(csv: File): Map<String, SpeciesMeta> {
            val out = HashMap<String, SpeciesMeta>(4096)
            csv.bufferedReader(Charset.forName("UTF-8")).useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) return@forEach
                    val parts = line.split(';', ',').map { it.trim() }.filter { it.isNotEmpty() }
                    if (parts.size < 2) return@forEach

                    // heuristiek: zoek numeric kolom = id
                    var id: String? = null
                    val strings = mutableListOf<String>()
                    parts.forEach { p ->
                        if (p.matches(Regex("\\d+")) && id == null) id = p else strings += p
                    }
                    if (id == null || strings.isEmpty()) return@forEach

                    // alias is meest specifieke (meestal langer); display = andere string
                    val alias = TextNormalizer.normalizeBasic(strings.maxByOrNull { it.length } ?: strings.first())
                    val display = strings.minByOrNull { it.length } ?: strings.first()
                    out[alias] = SpeciesMeta(id!!, display)
                }
            }
            return out
        }
    }
}
