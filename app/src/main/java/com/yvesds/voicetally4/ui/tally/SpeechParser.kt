package com.yvesds.voicetally4.ui.tally

import android.content.Context
import com.yvesds.voicetally4.ui.data.UnifiedAliasStore
import com.yvesds.voicetally4.utils.text.NumberWordsNL
import com.yvesds.voicetally4.utils.text.TextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SpeechParser(private val context: Context) {

    data class Result(
        val speciesId: String?,
        val delta: Int,
        val reset: Boolean
    )

    suspend fun parse(rawInput: String): Result {
        withContext(Dispatchers.IO) { UnifiedAliasStore.ensureReady(context) }

        val text = TextNormalizer.normalizeBasic(rawInput)

        val reset = text.contains("reset")

        // Eenvoudige “eerste getal”-extractie: check digits, anders check woorden met NumberWordsNL.parse
        val delta = extractFirstNumber(text) ?: 0

        // Heel simpele aliaskeuze: neem eerste token als alias
        val firstToken = text.split(' ', ',', ';').firstOrNull().orEmpty()
        val speciesId = UnifiedAliasStore.getSpeciesIdForAlias(firstToken)

        return Result(speciesId = speciesId, delta = delta, reset = reset)
    }

    private fun extractFirstNumber(text: String): Int? {
        // 1) direct digits zoeken
        val m = Regex("\\b\\d{1,4}\\b").find(text)
        if (m != null) return m.value.toIntOrNull()

        // 2) anders, check woorden (eerste dat parse() oplevert)
        for (tok in text.split(' ', ',', ';')) {
            NumberWordsNL.parse(tok)?.let { return it }
        }
        return null
    }
}
