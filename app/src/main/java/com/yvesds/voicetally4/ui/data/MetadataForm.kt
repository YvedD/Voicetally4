package com.yvesds.voicetally4.ui.data

/**
 * Basis meta-data voor de upload-json header.
 * Wordt later uitgebreid/aangepast in de volgende stap (serialisatie, validatie, enz.).
 */
data class MetadataForm(
    val observer: String,
    val project: String,
    val locatieType: String,
    val datumEpochMillis: Long,
    val weer: String,
    val methode: String,
    val opmerkingen: String
)
