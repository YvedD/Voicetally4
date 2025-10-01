package com.yvesds.voicetally4.ui.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ObservationRecord
 * - Alle velden als String (vereiste voor upload).
 * - @SerialName sluit aan op de payload-keys.
 * - EPOCH en datumtijd ook als String.
 */
@Serializable
data class ObservationRecord(
    @SerialName("_id") val _id: String = "",
    @SerialName("tellingid") val tellingid: String = "",
    @SerialName("soortid") val soortid: String = "",
    @SerialName("aantal") val aantal: String = "0",
    @SerialName("richting") val richting: String = "",
    @SerialName("aantalterug") val aantalterug: String = "0",
    @SerialName("richtingterug") val richtingterug: String = "",
    @SerialName("sightingdirection") val sightingdirection: String = "",
    @SerialName("lokaal") val lokaal: String = "0",

    // plus-velden
    @SerialName("aantal_plus") val aantal_plus: String = "0",
    @SerialName("aantalterug_plus") val aantalterug_plus: String = "0",
    @SerialName("lokaal_plus") val lokaal_plus: String = "0",

    // markering
    @SerialName("markeren") val markeren: String = "0",
    @SerialName("markerenlokaal") val markerenlokaal: String = "0",

    // attributen (gecodeerd volgens codes.json)
    @SerialName("geslacht") val geslacht: String = "",
    @SerialName("leeftijd") val leeftijd: String = "",
    @SerialName("kleed") val kleed: String = "",
    @SerialName("opmerkingen") val opmerkingen: String = "",
    @SerialName("trektype") val trektype: String = "",
    @SerialName("teltype") val teltype: String = "",

    // locatie & hoogte
    @SerialName("location") val location: String = "",
    @SerialName("height") val height: String = "",

    // tijd & groep
    @SerialName("tijdstip") val tijdstip: String = "",
    @SerialName("groupid") val groupid: String = "",
    @SerialName("uploadtijdstip") val uploadtijdstip: String = "",
    @SerialName("totaalaantal") val totaalaantal: String = "0"
)
