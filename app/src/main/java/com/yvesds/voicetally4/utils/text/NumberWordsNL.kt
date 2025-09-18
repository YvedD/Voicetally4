package com.yvesds.voicetally4.utils.text

/**
 * Zeer lichte NL getalwoorden parser.
 * Ondersteunt 0..999 in basisvormen (drie, twaalf, tweeëntwintig, honderd, honderd vijf, tweehonderd eenenzestig, ...).
 * Gebruik naast digits; we blijven strikt aan het patroon <alias> [<aantal>].
 */
object NumberWordsNL {
    private val units = mapOf(
        "nul" to 0, "een" to 1, "één" to 1, "twee" to 2, "drie" to 3, "vier" to 4, "vijf" to 5,
        "zes" to 6, "zeven" to 7, "acht" to 8, "negen" to 9
    )
    private val teens = mapOf(
        "tien" to 10, "elf" to 11, "twaalf" to 12, "dertien" to 13, "veertien" to 14,
        "vijftien" to 15, "zestien" to 16, "zeventien" to 17, "achttien" to 18, "negentien" to 19
    )
    private val tens = mapOf(
        "twintig" to 20, "dertig" to 30, "veertig" to 40, "vijftig" to 50,
        "zestig" to 60, "zeventig" to 70, "tachtig" to 80, "negentig" to 90
    )

    /**
     * Probeert 1 getalwoord (eventueel met 'en' samenstelling) te herkennen. Retourneert null bij twijfel.
     * Voorbeelden: "drie", "tweeëntwintig", "twee en twintig", "honderd", "honderd vijf", "tweehonderd eenenzestig".
     */
    fun parse(wordOrPhrase: String): Int? {
        val s = TextNormalizer.normalizeBasic(wordOrPhrase).replace("-", " ").replace("  ", " ")
        // Als het puur digits is, pak dat direct
        if (s.matches(Regex("\\d{1,4}"))) return s.toIntOrNull()

        // Split (soms spreken mensen "twee en twintig"); we proberen varianten.
        val parts = s.split(" ")
        return parseParts(parts) ?: parseParts(mergeAnd(parts))
    }

    private fun mergeAnd(parts: List<String>): List<String> {
        // combineer "en" varianten in 1 token: ["twee","en","twintig"] -> ["tweeëntwintig"]
        if (parts.size < 3) return parts
        val out = mutableListOf<String>()
        var i = 0
        while (i < parts.size) {
            if (i + 2 < parts.size && parts[i + 1] == "en" && tens.containsKey(parts[i + 2])) {
                out.add(parts[i] + "en" + parts[i + 2]) // twee + en + twintig -> tweeentwintig
                i += 3
            } else {
                out.add(parts[i]); i++
            }
        }
        return out
    }

    private fun parseParts(parts: List<String>): Int? {
        if (parts.isEmpty()) return null
        // Eenvoudige gevallen
        if (parts.size == 1) {
            val w = parts[0]
            units[w]?.let { return it }
            teens[w]?.let { return it }
            tens[w]?.let { return it }
            // samengestelde "tweeëntwintig"
            val split = splitCompoundTwentyLike(w) ?: return null
            return (units[split.first] ?: return null) + (tens[split.second] ?: return null)
        }

        // Honderdtallen
        var i = 0
        var total = 0
        if (parts[i] == "honderd") {
            total += 100; i++
        } else if (parts[i].endsWith("honderd")) {
            val u = parts[i].removeSuffix("honderd")
            val base = units[u] ?: return null
            total += base * 100; i++
        }

        // Rest (tientallen/eenheden)
        if (i < parts.size) {
            val rest = parts.drop(i).joinToString(" ")
            val restNum = parse(rest) ?: return null
            total += restNum
        }
        return if (total > 0) total else null
    }

    private fun splitCompoundTwentyLike(token: String): Pair<String, String>? {
        // naive: detecteer "...en..." met tens-suffix
        tens.keys.forEach { ten ->
            val idx = token.indexOf("en$ten")
            if (idx > 0) {
                val unit = token.substring(0, idx)
                if (units.containsKey(unit)) return unit to ten
            }
        }
        return null
    }
}
