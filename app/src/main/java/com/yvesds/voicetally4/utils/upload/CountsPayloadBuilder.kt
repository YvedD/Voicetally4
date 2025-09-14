package com.yvesds.voicetally4.utils.upload

import com.yvesds.voicetally4.ui.data.MetadataForm
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Maakt de JSON payload voor /api/counts_save.
 * - seconds-since-epoch voor begin/eind (server hanteert seconden).
 * - Mapt UI-waarden naar wat de API verwacht.
 */
object CountsPayloadBuilder {

    /** Map spinner label -> API code voor typetelling. */
    private fun mapTypeTelling(label: String): String = when (label.trim().lowercase(Locale.ROOT)) {
        "alle soorten" -> "all"
        "zeetrek" -> "sea"
        "ooievaars en roofvogels" -> "raptor"
        else -> "all"
    }

    /** Map windrichting weergave (N, NO, ONO, ...) naar API code (n, no, ono, ...) */
    private fun mapWindrichting(display: String): String {
        val s = display.trim().lowercase(Locale.ROOT)
        return s.replace(" ", "")
    }

    /** Map neerslag label -> API code. */
    private fun mapNeerslag(label: String): String = when (label.trim().lowercase(Locale.ROOT)) {
        "geen" -> "geen"
        "regen" -> "regen"
        "motregen" -> "motregen"
        "mist" -> "mist"
        "hagel" -> "hagel"
        "sneeuw" -> "sneeuw"
        "sneeuw- of zandstorm", "sneeuw - of zandstorm", "sneeuw/zandstorm", "sneeuw of zandstorm" -> "sneeuw_zandstorm"
        "onweer" -> "onweer"
        else -> ""
    }

    /** Map windkracht-spinner (“var”, “<1bf”, “1bf”…“11bf”, “>11bf”) → puur cijfer als String (bv. "0","1",…,"12") */
    private fun mapWindkrachtToIntString(label: String?): String {
        if (label.isNullOrBlank()) return ""
        val v = label.trim().lowercase(Locale.ROOT)
        return when {
            v == "var" -> "0"
            v == "<1bf" -> "0"
            v == ">11bf" -> "12"                   // bovengrens als 12
            v.endsWith("bf") -> {
                val n = v.removeSuffix("bf").toIntOrNull()
                if (n == null) "" else n.coerceIn(0, 12).toString()
            }
            else -> ""
        }
    }

    /** Map bewolking spinner (“0/8”..“8/8”) → enkel cijfer "0".."8" zoals DB verwacht (integer). */
    private fun mapBewolkingAchtsten(label: String?): String {
        if (label.isNullOrBlank()) return ""
        val n = label.substringBefore('/').trim().toIntOrNull() ?: return ""
        return n.coerceIn(0, 8).toString()
    }

    /** Hardcoded voorbeeld-waarnemingen (zoals aangeleverd). */
    private fun buildHardcodedData(): JSONArray = JSONArray().apply {
        put(JSONObject().apply {
            put("_id", "14")
            put("tellingid", "28")
            put("soortid", "31")
            put("aantal", "1")
            put("richting", "w")
            put("aantalterug", "2")
            put("richtingterug", "o")
            put("sightingdirection", "NW")
            put("lokaal", "3")
            put("aantal_plus", "0")
            put("aantalterug_plus", "0")
            put("lokaal_plus", "0")
            put("markeren", "1")
            put("markerenlokaal", "1")
            put("geslacht", "M")
            put("leeftijd", "A")
            put("kleed", "L")
            put("opmerkingen", "Remarks")
            put("trektype", "R")
            put("teltype", "C")
            put("location", "")
            put("height", "L")
            put("tijdstip", "1756721135")
            put("groupid", "14")
            put("uploadtijdstip", "2025-09-01 10:05:39")
            put("totaalaantal", "3")
        })
        put(JSONObject().apply {
            put("_id", "13")
            put("tellingid", "28")
            put("soortid", "254")
            put("aantal", "300")
            put("richting", "")
            put("aantalterug", "0")
            put("richtingterug", "")
            put("sightingdirection", "")
            put("lokaal", "0")
            put("aantal_plus", "0")
            put("aantalterug_plus", "0")
            put("lokaal_plus", "0")
            put("markeren", "0")
            put("markerenlokaal", "0")
            put("geslacht", "")
            put("leeftijd", "")
            put("kleed", "")
            put("opmerkingen", "")
            put("trektype", "")
            put("teltype", "")
            put("location", "")
            put("height", "M")
            put("tijdstip", "1756210093")
            put("groupid", "13")
            put("uploadtijdstip", "2025-09-01 10:05:39")
            put("totaalaantal", "300")
        })
        put(JSONObject().apply {
            put("_id", "12")
            put("tellingid", "28")
            put("soortid", "105")
            put("aantal", "1")
            put("richting", "")
            put("aantalterug", "0")
            put("richtingterug", "")
            put("sightingdirection", "")
            put("lokaal", "0")
            put("aantal_plus", "0")
            put("aantalterug_plus", "0")
            put("lokaal_plus", "0")
            put("markeren", "0")
            put("markerenlokaal", "0")
            put("geslacht", "")
            put("leeftijd", "")
            put("kleed", "")
            put("opmerkingen", "")
            put("trektype", "")
            put("teltype", "")
            put("location", "")
            put("height", "M")
            put("tijdstip", "1756209152")
            put("groupid", "12")
            put("uploadtijdstip", "2025-09-01 10:05:39")
            put("totaalaantal", "1")
        })
        put(JSONObject().apply {
            put("_id", "11")
            put("tellingid", "28")
            put("soortid", "94")
            put("aantal", "1")
            put("richting", "")
            put("aantalterug", "0")
            put("richtingterug", "")
            put("sightingdirection", "ENE")
            put("lokaal", "0")
            put("aantal_plus", "0")
            put("aantalterug_plus", "0")
            put("lokaal_plus", "0")
            put("markeren", "0")
            put("markerenlokaal", "0")
            put("geslacht", "")
            put("leeftijd", "")
            put("kleed", "")
            put("opmerkingen", "")
            put("trektype", "")
            put("teltype", "")
            put("location", "")
            put("height", "M")
            put("tijdstip", "1756208899")
            put("groupid", "11")
            put("uploadtijdstip", "2025-09-01 10:05:39")
            put("totaalaantal", "1")
        })
    }

    /** Zelfde hardcoded set, maar met verdubbelde aantallen (aantal, aantalterug, totaalaantal). */
    private fun buildHardcodedDataDoubled(): JSONArray = JSONArray().apply {
        put(JSONObject().apply {
            put("_id", "14")
            put("tellingid", "28")
            put("soortid", "31")
            put("aantal", "2")           // 1 -> 2
            put("richting", "w")
            put("aantalterug", "4")      // 2 -> 4
            put("richtingterug", "o")
            put("sightingdirection", "NW")
            put("lokaal", "3")
            put("aantal_plus", "0")
            put("aantalterug_plus", "0")
            put("lokaal_plus", "0")
            put("markeren", "1")
            put("markerenlokaal", "1")
            put("geslacht", "M")
            put("leeftijd", "A")
            put("kleed", "L")
            put("opmerkingen", "Remarks")
            put("trektype", "R")
            put("teltype", "C")
            put("location", "")
            put("height", "L")
            put("tijdstip", "1756721135")
            put("groupid", "14")
            put("uploadtijdstip", "2025-09-01 10:05:39")
            put("totaalaantal", "6")     // 3 -> 6
        })
        put(JSONObject().apply {
            put("_id", "13")
            put("tellingid", "28")
            put("soortid", "254")
            put("aantal", "600")         // 300 -> 600
            put("richting", "")
            put("aantalterug", "0")
            put("richtingterug", "")
            put("sightingdirection", "")
            put("lokaal", "0")
            put("aantal_plus", "0")
            put("aantalterug_plus", "0")
            put("lokaal_plus", "0")
            put("markeren", "0")
            put("markerenlokaal", "0")
            put("geslacht", "")
            put("leeftijd", "")
            put("kleed", "")
            put("opmerkingen", "")
            put("trektype", "")
            put("teltype", "")
            put("location", "")
            put("height", "M")
            put("tijdstip", "1756210093")
            put("groupid", "13")
            put("uploadtijdstip", "2025-09-01 10:05:39")
            put("totaalaantal", "600")   // 300 -> 600
        })
        put(JSONObject().apply {
            put("_id", "12")
            put("tellingid", "28")
            put("soortid", "105")
            put("aantal", "2")           // 1 -> 2
            put("richting", "")
            put("aantalterug", "0")
            put("richtingterug", "")
            put("sightingdirection", "")
            put("lokaal", "0")
            put("aantal_plus", "0")
            put("aantalterug_plus", "0")
            put("lokaal_plus", "0")
            put("markeren", "0")
            put("markerenlokaal", "0")
            put("geslacht", "")
            put("leeftijd", "")
            put("kleed", "")
            put("opmerkingen", "")
            put("trektype", "")
            put("teltype", "")
            put("location", "")
            put("height", "M")
            put("tijdstip", "1756209152")
            put("groupid", "12")
            put("uploadtijdstip", "2025-09-01 10:05:39")
            put("totaalaantal", "2")     // 1 -> 2
        })
        put(JSONObject().apply {
            put("_id", "11")
            put("tellingid", "28")
            put("soortid", "94")
            put("aantal", "2")           // 1 -> 2
            put("richting", "")
            put("aantalterug", "0")
            put("richtingterug", "")
            put("sightingdirection", "ENE")
            put("lokaal", "0")
            put("aantal_plus", "0")
            put("aantalterug_plus", "0")
            put("lokaal_plus", "0")
            put("markeren", "0")
            put("markerenlokaal", "0")
            put("geslacht", "")
            put("leeftijd", "")
            put("kleed", "")
            put("opmerkingen", "")
            put("trektype", "")
            put("teltype", "")
            put("location", "")
            put("height", "M")
            put("tijdstip", "1756208899")
            put("groupid", "11")
            put("uploadtijdstip", "2025-09-01 10:05:39")
            put("totaalaantal", "2")     // 1 -> 2
        })
    }

    /**
     * Standaard build (nieuwe telling).
     */
    fun build(
        form: MetadataForm,
        beginSec: Long,
        eindSec: Long,
        weerOpmerking: String,
        telpostId: String
    ): String {
        val tempIntStr = form.temperatuurC?.roundToInt()?.toString() ?: ""
        val zichtIntStr = form.zichtKm?.toInt()?.toString() ?: ""     // meters
        val hpaIntStr = form.luchtdrukHpa?.roundToInt()?.toString() ?: ""
        val windrichtingCode = if (form.windrichting.isNullOrBlank()) "" else mapWindrichting(form.windrichting!!)
        val neerslagCode = if (form.neerslag.isNullOrBlank()) "" else mapNeerslag(form.neerslag!!)
        val typeTellingCode = mapTypeTelling(form.typeTelling)
        val windkrachtIntStr = mapWindkrachtToIntString(form.windkrachtBft)
        val bewolkingIntStr = mapBewolkingAchtsten(form.bewolkingAchtsten)
        val uuid = "Trektellen_Android_1.8.45_${UUID.randomUUID()}"

        val telling = JSONObject().apply {
            put("externid", "Android App 1.8.45")
            put("timezoneid", "Europe/Brussels")
            put("bron", "4")
            put("_id", "")
            put("tellingid", "")
            put("telpostid", telpostId)
            put("begintijd", beginSec.toString())
            put("eindtijd", eindSec.toString())
            put("tellers", form.tellersNaam)
            put("weer", weerOpmerking)
            put("windrichting", windrichtingCode)
            put("windkracht", windkrachtIntStr)
            put("temperatuur", tempIntStr)
            put("bewolking", bewolkingIntStr)
            put("bewolkinghoogte", "")
            put("neerslag", neerslagCode)
            put("duurneerslag", "")
            put("zicht", zichtIntStr)
            put("tellersactief", "")
            put("tellersaanwezig", "")
            put("typetelling", typeTellingCode)
            put("metersnet", "")
            put("geluid", "")
            put("opmerkingen", form.opmerkingen ?: "")
            put("onlineid", "")
            put("HYDRO", "")
            put("hpa", hpaIntStr)
            put("equipment", "")
            put("uuid", uuid)
            put("uploadtijdstip", "")
            put("nrec", "0")
            put("nsoort", "0")
            put("data", JSONArray())
        }

        val root = JSONArray().apply { put(telling) }
        return root.toString()
    }

    /**
     * Build voor **update van een bestaande telling**:
     * - onlineid wordt meegegeven (bepaalt welke telling geüpdatet wordt).
     * - gebruikt dezelfde mapping, maar plaatst hardcoded, verdubbelde waarnemingen in "data"
     *   en zet nrec/nsoort op "4".
     */
    fun buildUpdateDemo(
        form: MetadataForm,
        beginSec: Long,
        eindSec: Long,
        weerOpmerking: String,
        telpostId: String,
        onlineId: String
    ): String {
        val tempIntStr = form.temperatuurC?.roundToInt()?.toString() ?: ""
        val zichtIntStr = form.zichtKm?.toInt()?.toString() ?: ""
        val hpaIntStr = form.luchtdrukHpa?.roundToInt()?.toString() ?: ""
        val windrichtingCode = if (form.windrichting.isNullOrBlank()) "" else mapWindrichting(form.windrichting!!)
        val neerslagCode = if (form.neerslag.isNullOrBlank()) "" else mapNeerslag(form.neerslag!!)
        val typeTellingCode = mapTypeTelling(form.typeTelling)
        val windkrachtIntStr = mapWindkrachtToIntString(form.windkrachtBft)
        val bewolkingIntStr = mapBewolkingAchtsten(form.bewolkingAchtsten)
        val uuid = "Trektellen_Android_1.8.45_${UUID.randomUUID()}"

        val telling = JSONObject().apply {
            put("externid", "Android App 1.8.45")
            put("timezoneid", "Europe/Brussels")
            put("bron", "4")
            put("_id", "")
            put("tellingid", "")
            put("telpostid", telpostId)
            put("begintijd", beginSec.toString())
            put("eindtijd", eindSec.toString())
            put("tellers", form.tellersNaam)
            put("weer", weerOpmerking)
            put("windrichting", windrichtingCode)
            put("windkracht", windkrachtIntStr)
            put("temperatuur", tempIntStr)
            put("bewolking", bewolkingIntStr)
            put("bewolkinghoogte", "")
            put("neerslag", neerslagCode)
            put("duurneerslag", "")
            put("zicht", zichtIntStr)
            put("tellersactief", "")
            put("tellersaanwezig", "")
            put("typetelling", typeTellingCode)
            put("metersnet", "")
            put("geluid", "")
            put("opmerkingen", form.opmerkingen ?: "")
            put("onlineid", onlineId)                 // <<< belangrijk voor update
            put("HYDRO", "")
            put("hpa", hpaIntStr)
            put("equipment", "")
            put("uuid", uuid)
            put("uploadtijdstip", "")

            // Hardcoded verdubbelde waarnemingen:
            put("nrec", "4")
            put("nsoort", "4")
            put("data", buildHardcodedDataDoubled())
        }

        val root = JSONArray().apply { put(telling) }
        return root.toString()
    }
}
