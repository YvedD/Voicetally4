package com.yvesds.voicetally4.utils.text

import java.text.Normalizer

object TextNormalizer {
    /** Lowercase, verwijder diacritics, collapse whitespaces, trim. */
    fun normalizeBasic(input: String): String {
        val lower = input.lowercase()
        val norm = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val noDiacritics = norm.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return noDiacritics.replace("\\s+".toRegex(), " ").trim()
    }
}
