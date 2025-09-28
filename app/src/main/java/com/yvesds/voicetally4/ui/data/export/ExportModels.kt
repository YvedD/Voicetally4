package com.yvesds.voicetally4.utils.upload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Upload “envelope”: de server verwacht een JSON array met één object.
 * Voor nu sturen we enkel de header en laten we data[] leeg.
 */
typealias UploadEnvelope = List<UploadHeader>

@Serializable
data class UploadHeader(
    @SerialName("externid") val externId: String = "",
    @SerialName("timezoneid") val timezoneId: String = "",
    @SerialName("bron") val bron: String = "2",                 // 2 = Android (conform huidig gebruik)
    @SerialName("_id") val _id: String = "",                    // lokale (tijdelijke) id; leeg laten
    @SerialName("tellingid") val tellingId: String = "",        // idem; server kan dit teruggeven
    @SerialName("telpostid") val telpostId: String = "",
    @SerialName("begintijd") val beginTijdSec: String = "",     // epoch sec (string)
    @SerialName("eindtijd") val eindTijdSec: String = "",       // epoch sec (string)

    @SerialName("tellers") val tellers: String = "",
    @SerialName("weer") val weer: String = "",
    @SerialName("windrichting") val windRichting: String = "",
    @SerialName("windkracht") val windKracht: String = "",
    @SerialName("temperatuur") val temperatuur: String = "",
    @SerialName("bewolking") val bewolking: String = "",
    @SerialName("bewolkinghoogte") val bewolkingHoogte: String = "",
    @SerialName("neerslag") val neerslag: String = "",
    @SerialName("duurneerslag") val duurNeerslag: String = "",
    @SerialName("zicht") val zicht: String = "",
    @SerialName("tellersactief") val tellersActief: String = "",
    @SerialName("tellersaanwezig") val tellersAanwezig: String = "",

    // Belangrijk: typetelling is de CODE (“all” / “sea” / …), niet het label.
    @SerialName("typetelling") val typeTellingCode: String = "",

    @SerialName("metersnet") val metersNet: String = "",
    @SerialName("geluid") val geluid: String = "",
    @SerialName("opmerkingen") val opmerkingen: String = "",

    // Door server gezet; in start-payload leeg laten
    @SerialName("onlineid") val onlineId: String = "",

    @SerialName("HYDRO") val hydro: String = "",
    @SerialName("hpa") val hpa: String = "",
    @SerialName("equipment") val equipment: String = "",

    @SerialName("uuid") val uuid: String = "",
    @SerialName("uploadtijdstip") val uploadTijdstip: String = "",

    // Voor nu expliciet leeg laten zoals gevraagd
    @SerialName("nrec") val nRec: String = "",
    @SerialName("nsoort") val nSoort: String = "",

    @SerialName("data") val data: List<UploadRecord> = emptyList()
)

/**
 * Recordmodel voor volledigheid – we sturen ze nu NIET mee (data = []).
 * Deze velden zijn afgeleid uit het voorbeeldbestand.
 */
@Serializable
data class UploadRecord(
    @SerialName("_id") val _id: String = "",
    @SerialName("tellingid") val tellingId: String = "",
    @SerialName("soortid") val soortId: String = "",
    @SerialName("aantal") val aantal: String = "",
    @SerialName("richting") val richting: String = "",
    @SerialName("aantalterug") val aantalTerug: String = "",
    @SerialName("richtingterug") val richtingTerug: String = "",
    @SerialName("sightingdirection") val sightingDirection: String = "",
    @SerialName("lokaal") val lokaal: String = "",
    @SerialName("aantal_plus") val aantalPlus: String = "",
    @SerialName("aantalterug_plus") val aantalTerugPlus: String = "",
    @SerialName("lokaal_plus") val lokaalPlus: String = "",
    @SerialName("markeren") val markeren: String = "",
    @SerialName("markerenlokaal") val markerenLokaal: String = "",
    @SerialName("geslacht") val geslacht: String = "",
    @SerialName("leeftijd") val leeftijd: String = "",
    @SerialName("kleed") val kleed: String = "",
    @SerialName("opmerkingen") val opmerkingen: String = "",
    @SerialName("trektype") val trekType: String = "",
    @SerialName("teltype") val telType: String = "",
    @SerialName("location") val location: String = "",
    @SerialName("height") val height: String = "",
    @SerialName("tijdstip") val tijdstipSec: String = "",      // epoch sec (string)
    @SerialName("groupid") val groupId: String = "",
    @SerialName("uploadtijdstip") val uploadTijdstip: String = "",
    @SerialName("totaalaantal") val totaalAantal: String = ""
)
