package com.yvesds.voicetally4.ui.tally

import android.content.Context
import com.yvesds.voicetally4.ui.data.UnifiedAliasStore
import com.yvesds.voicetally4.utils.io.StorageUtils
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import kotlin.math.max

/**
 * Spraakparser met alias-index. Eerst proberen we de unified store te gebruiken;
 * zo niet, dan val je terug op de legacy aliasmapping.bin (VT4ALIAS1).
 */
class SpeechParser(private val context: Context) {

    data class Match(
        val speciesId: String,
        val displayName: String,
        val amount: Int
    )

    private val index: Map<String, SpeciesMeta> by lazy { loadIndex(context) }

    private fun loadIndex(ctx: Context): Map<String, SpeciesMeta> {
        // 1) Unified speech-sectie
        val unified = UnifiedAliasStore.loadSpeechIndex(ctx)
        if (unified.isNotEmpty()) {
            return unified.mapValues { SpeciesMeta(it.value.id, it.value.displayName) }
        }

        // 2) Legacy VT4ALIAS1
        return loadLegacyAliasIndex(ctx)
    }

    private fun loadLegacyAliasIndex(ctx: Context): Map<String, SpeciesMeta> {
        val binDir = StorageUtils.getPublicAppDir(ctx, "binaries")
        val file = File(binDir, "aliasmapping.bin")
        val map = HashMap<String, SpeciesMeta>()
        if (!file.exists() || !file.canRead()) return map

        try {
            DataInputStream(BufferedInputStream(file.inputStream())).use { din ->
                val magic = ByteArray(9) // "VT4ALIAS1" (9 incl. EOS in sommige builds)
                din.readFully(magic, 0, 9.coerceAtMost(magic.size))
                val header = String(magic).trimEnd('\u0000')
                if (!header.startsWith("VT4ALIAS1")) return map

                while (true) {
                    val alias = din.readUTF()
                    val id = din.readUTF()
                    val display = din.readUTF()
                    map[normalize(alias)] = SpeciesMeta(id, display)
                }
            }
        } catch (_: Throwable) {
            // EOF/format -> ok; best-effort
        }
        return map
    }

    private data class SpeciesMeta(val id: String, val displayName: String)

    fun parseOneUtterance(text: String): List<Match> {
        val words = text.trim().lowercase()
        if (words.isBlank()) return emptyList()

        // dummy parser: identiek aan jouw vroegere aanpak (langste alias eerst)
        val tokens = words.split(Regex("\\s+"))
        val results = ArrayList<Match>()

        var i = 0
        while (i < tokens.size) {
            var best: Pair<Int, SpeciesMeta>? = null
            var bestLen = 0

            // Probeer substrings met aflopende lengte
            var len = minOf(5, tokens.size - i) // cap 5 woorden
            while (len > 0) {
                val phrase = tokens.subList(i, i + len).joinToString(" ")
                val meta = index[phrase]
                if (meta != null) {
                    if (len > bestLen) {
                        bestLen = len
                        best = i to meta
                        break
                    }
                }
                len--
            }

            if (best != null) {
                val (_, meta) = best
                results.add(Match(speciesId = meta.id, displayName = meta.displayName, amount = 1))
                i += max(1, bestLen)
            } else {
                i++
            }
        }

        return results
    }

    private fun normalize(s: String): String = s.trim().lowercase()
}
