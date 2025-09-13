package com.yvesds.voicetally4.ui.data

/**
 * Metadata die nodig is voor export.
 * Aangepast:
 * - 'project' vervangen door 'typeTelling'
 * - veld 'methode' verwijderd
 * - tijd expliciet als HH:mm naast epoch
 * - telpost-label & -code toegevoegd
 * - weer- en teller-velden toegevoegd
 */
data class MetadataForm(
    // JSON voorbereiding
    // Type telling en locatie
    val typeTelling: String,
    val locatieType: String,
    // Datum & tijd
    val datumEpochMillis: Long,
    val timeHHmm: String,
    // Telpost
    val telpostLabel: String,
    val telpostCode: String,
    // Teller(s)
    val tellersNaam: String,
    val tellersId: String,
    // Weer
    val windrichting: String,
    val temperatuurC: Double?,
    val bewolkingAchtsten: String,
    val neerslag: String,
    val zichtKm: Double?,
    val windkrachtBft: String,
    val luchtdrukHpa: Double?,
    // Extra
    val opmerkingen: String
)
