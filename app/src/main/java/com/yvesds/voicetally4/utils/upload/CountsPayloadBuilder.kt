package com.yvesds.voicetally4.utils.upload

import com.yvesds.voicetally4.ui.data.MetadataForm
import com.yvesds.voicetally4.utils.io.CodesJsonReader
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Bouwt de start-payload met kotlinx.serialization.
 * Vereisten (expliciet):
 * - "externid"      = "Android App 1.8.45" (vast)
 * - "timezoneid"    = "Europe/Brussels"    (vast)
 * - "bron"          = "4"
 * - "temperatuur"   = afgerond op dichtstbijzijnde geheel getal, als string
 * - "uploadtijdstip"= huidige lokale tijd in Europe/Brussels, "yyyy-MM-dd HH:mm:ss"
 * - data[] = leeg, nrec/nsoort = ""
 * - typetelling = servercode via CodesJsonReader (label → code)
 * - geen BuildConfig, geen Context
 */
object CountsPayloadBuilder {

    private const val EXTERN_ID = "Android App 1.8.45"
    private const val TIMEZONE_ID = "Europe/Brussels"
    private const val BRON = "4"

    private val json by lazy {
        Json {
            prettyPrint = false
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }

    // Alleen voor debug/logging-bestand
    private val jsonPretty by lazy {
        Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }

    private val uploadStampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val zoneBrussels: ZoneId = ZoneId.of(TIMEZONE_ID)

    /**
     * Compacte payload die je POST naar de server.
     */
    fun buildStart(
        form: MetadataForm,
        beginSec: Long,
        eindSec: Long,
        weerOpmerking: String,
        telpostId: String
    ): String {
        val header = buildHeader(form, beginSec, eindSec, weerOpmerking, telpostId)
        val envelope: UploadEnvelope = listOf(header)
        return json.encodeToString(envelope)
    }

    /**
     * Zelfde payload, pretty-printed (handig voor logs/bestand).
     */
    fun buildStartPretty(
        form: MetadataForm,
        beginSec: Long,
        eindSec: Long,
        weerOpmerking: String,
        telpostId: String
    ): String {
        val header = buildHeader(form, beginSec, eindSec, weerOpmerking, telpostId)
        val envelope: UploadEnvelope = listOf(header)
        return jsonPretty.encodeToString(envelope)
    }

    // ---- core opbouw ----

    private fun buildHeader(
        form: MetadataForm,
        beginSec: Long,
        eindSec: Long,
        weerOpmerking: String,
        telpostId: String
    ): UploadHeader {
        val typeTellingCode = mapTypeTellingToCode(form.typeTelling)

        // Temperatuur: afronden naar dichtstbijzijnde integer, als string
        val tempRounded: String = form.temperatuurC
            ?.let { it.roundToInt().toString() }
            ?: ""

        // Upload timestamp in Europe/Brussels
        val uploadTs = LocalDateTime.now(zoneBrussels).format(uploadStampFormatter)

        return UploadHeader(
            externId = EXTERN_ID,
            timezoneId = TIMEZONE_ID,
            bron = BRON,
            _id = "",
            tellingId = "",
            telpostId = telpostId.orEmpty(),
            beginTijdSec = beginSec.toString(),
            eindTijdSec = eindSec.toString(),

            tellers = form.tellersNaam.orEmpty(),
            weer = "",
            windRichting = form.windrichting.orEmpty(),
            windKracht = mapWindkrachtToServer(form.windkrachtBft),   // "0".."12"
            temperatuur = tempRounded,                                // afgerond geheel getal
            bewolking = mapBewolkingToServer(form.bewolkingAchtsten), // "0".."8"
            bewolkingHoogte = "",
            neerslag = form.neerslag.orEmpty(),
            duurNeerslag = "",
            zicht = form.zichtKm?.let { safeNum(it) }.orEmpty(),
            tellersActief = "",
            tellersAanwezig = "",

            typeTellingCode = typeTellingCode,

            metersNet = "",
            geluid = "",
            opmerkingen = buildOpmerkingen(weerOpmerking, form.opmerkingen),

            onlineId = "",

            hydro = "",
            hpa = form.luchtdrukHpa?.let { safeNum(it) }.orEmpty(),
            equipment = "",

            uuid = buildUuid(),
            uploadTijdstip = uploadTs,       // exact "yyyy-MM-dd HH:mm:ss" in Europe/Brussels

            nRec = "",
            nSoort = "",

            data = emptyList()
        )
    }

    // ---- mapping helpers ----

    /** Label → code (“all”, “sea”, …) voor veld "typetelling_trek". Geen match ⇒ leeg. */
    private fun mapTypeTellingToCode(chosenLabel: String?): String {
        val label = chosenLabel?.trim().orEmpty()
        if (label.isEmpty()) return ""
        val items = CodesJsonReader.getItems("typetelling_trek")
        val match = items.firstOrNull { it.tekst.equals(label, ignoreCase = true) }
        return match?.waarde?.takeIf { it.isNotEmpty() } ?: ""
    }

    /**
     * Server verwacht **integer** voor windkracht (als string).
     * Mapping:
     *   "var" / "<1bf" -> "0"
     *   "1bf".."11bf"  -> "1".."11"
     *   ">11bf"        -> "12"
     *   Anders (al nummer) → laat door.
     */
    private fun mapWindkrachtToServer(label: String?): String {
        val raw = label?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (raw.isEmpty()) return ""
        return when {
            raw == "var" || raw == "<1bf" -> "0"
            raw == ">11bf" -> "12"
            raw.endsWith("bf") -> raw.removeSuffix("bf")
                .toIntOrNull()?.coerceIn(0, 12)?.toString() ?: ""
            raw.toIntOrNull() != null -> raw // al "0".."12"
            else -> "" // onbekend → leeg
        }
    }

    /**
     * Server verwacht **0..8** (als string), geen "/8".
     * Accepteert "3/8" → "3", of al "0".."8".
     */
    private fun mapBewolkingToServer(label: String?): String {
        val raw = label?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        val slash = raw.indexOf('/')
        val head = if (slash >= 0) raw.substring(0, slash) else raw
        return head.toIntOrNull()?.coerceIn(0, 8)?.toString() ?: ""
    }

    /** UUID in gewenst formaat, zonder versienaam. */
    private fun buildUuid(): String = "Trektellen_Android_${UUID.randomUUID()}"

    /** Locale-neutrale nummerstring zonder overbodige decimalen. */
    private fun safeNum(d: Double): String {
        val s = String.format(Locale.US, "%.3f", d).trimEnd('0').trimEnd('.')
        return s
    }

    private fun buildOpmerkingen(weerOpmerking: String, extra: String): String {
        val a = weerOpmerking.trim()
        val b = extra.trim()
        return when {
            a.isEmpty() && b.isEmpty() -> ""
            a.isNotEmpty() && b.isEmpty() -> a
            a.isEmpty() && b.isNotEmpty() -> b
            else -> "$a — $b"
        }
    }
}
