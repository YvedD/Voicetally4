package com.yvesds.voicetally4.ui.data

/**
 * CSV-indeling:
 *  kolom 0 = soortId    (String, numeriek in tekstvorm)
 *  kolom 1 = canonical  (lowercase canonical soortnaam)
 *  kolom 2 = tileName   (erkende afkorting → TEKST OP TILE)
 *  kolom 3..23 = alias1..alias21 (optioneel, voor spraakinvoer later)
 */
data class SoortAlias(
    val soortId: String,
    val canonical: String,
    val tileName: String,
    val aliases: List<String>
)
