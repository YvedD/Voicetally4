package com.yvesds.voicetally4.utils.upload

import android.content.Context
import com.yvesds.voicetally4.ui.data.MetadataForm
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Snel en allocatie-arm JSON-builder voor Trektellen /api/counts_save.
 *
 * Kenmerken
 * - StringBuilder i.p.v. JSONObject/JSONArray → minder GC en sneller.
 * - Geen I/O, geen main-thread werk; pure CPU.
 * - Payload blijft identiek qua veldnamen/waardetypes (strings waar jij ze als string postte).
 * - Geen demo/hardcoded records meer. Je kunt records zelf aanleveren als JSON-string (dataArrayJson).
 * - Timezone wordt standaard als "Europe/Brussels" gezet; JSON-escape converteert dit naar "Europe\/Brussels".
 *
 * Gebruik
 * - buildStart(...): start een telling zonder records (nrec/nsoort = "0", data = []).
 * - buildWithData(...): voortzetten/aanpassen met onlineId + jouw eigen data-array (als JSON-string).
 * - buildWithDataUsingPrefs(...): variant die onlineId uit prefs haalt (upload_prefs/last_onlineid).
 */
object CountsPayloadBuilder {

    // ======================
    // Publieke API
    // ======================

    /**
     * Bouw payload voor een **nieuwe telling** (zonder onlineid) en **zonder** records.
     * - nrec/nsoort = "0"
     * - data = []
     */
    fun buildStart(
        form: MetadataForm,
        beginSec: Long,
        eindSec: Long,
        weerOpmerking: String,
        telpostId: String
    ): String {
        return buildInternal(
            form = form,
            beginSec = beginSec,
            eindSec = eindSec,
            weerOpmerking = weerOpmerking,
            telpostId = telpostId,
            onlineId = "", // nieuwe telling
            nrec = "0",
            nsoort = "0",
            dataArrayJson = "[]"
        )
    }

    /**
     * Bouw payload voor **voortzetten/aanpassen** met reeds gekende onlineId en jouw eigen data-array.
     * - Geef nrec/nsoort mee (vaak gelijk aan aantal records en aantal soorten).
     * - dataArrayJson moet geldige JSON zijn: bv. `[{"_id":"...","soortid":"...","aantal":"..."}]`
     */
    fun buildWithData(
        form: MetadataForm,
        beginSec: Long,
        eindSec: Long,
        weerOpmerking: String,
        telpostId: String,
        onlineId: String,
        nrec: Int,
        nsoort: Int,
        dataArrayJson: String
    ): String {
        return buildInternal(
            form = form,
            beginSec = beginSec,
            eindSec = eindSec,
            weerOpmerking = weerOpmerking,
            telpostId = telpostId,
            onlineId = onlineId,
            nrec = nrec.toString(),
            nsoort = nsoort.toString(),
            dataArrayJson = dataArrayJson
        )
    }

    /**
     * Convenience: lees onlineId uit SharedPreferences (upload_prefs/last_onlineid) en bouw payload met data.
     * @return Result.success(json) of Result.failure als onlineId ontbreekt.
     */
    fun buildWithDataUsingPrefs(
        context: Context,
        form: MetadataForm,
        beginSec: Long,
        eindSec: Long,
        weerOpmerking: String,
        telpostId: String,
        nrec: Int,
        nsoort: Int,
        dataArrayJson: String,
        prefsName: String = "upload_prefs",
        keyOnlineId: String = "last_onlineid"
    ): Result<String> {
        val onlineId = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getString(keyOnlineId, null)
            ?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("Geen onlineid in prefs ($prefsName/$keyOnlineId)"))
        return Result.success(
            buildWithData(
                form = form,
                beginSec = beginSec,
                eindSec = eindSec,
                weerOpmerking = weerOpmerking,
                telpostId = telpostId,
                onlineId = onlineId,
                nrec = nrec,
                nsoort = nsoort,
                dataArrayJson = dataArrayJson
            )
        )
    }

    // ======================
    // Interne bouwlogica
    // ======================

    private fun buildInternal(
        form: MetadataForm,
        beginSec: Long,
        eindSec: Long,
        weerOpmerking: String,
        telpostId: String,
        onlineId: String,
        nrec: String,
        nsoort: String,
        dataArrayJson: String
    ): String {
        // Strings voor numerieke velden (zoals jij het al verstuurde)
        val tempStr = form.temperatuurC?.roundToInt()?.toString() ?: ""
        val zichtMetersStr = form.zichtKm?.toInt()?.toString() ?: "" // visibility in meters
        val hpaStr = form.luchtdrukHpa?.roundToInt()?.toString() ?: ""

        val windrichtingCode = form.windrichting?.let { mapWindrichting(it) } ?: ""
        val neerslagCode = form.neerslag?.let { mapNeerslag(it) } ?: ""
        val typeTellingCode = mapTypeTelling(form.typeTelling)
        val windkrachtStr = mapWindkrachtToIntString(form.windkrachtBft)
        val bewolkingStr = mapBewolkingAchtsten(form.bewolkingAchtsten)

        val uuid = "Trektellen_Android_1.8.45_${UUID.randomUUID()}"

        // JSON bouwen: enkele velden zijn expliciet "" conform de vorige implementatie
        val sb = StringBuilder(1024)
        sb.append('[').append('{')
        var first = true

        fun add(name: String, value: String) {
            if (!first) sb.append(',')
            first = false
            sb.append('"').append(name).append('"').append(':').append('"').append(escapeJson(value)).append('"')
        }

        add("externid", "Android App 1.8.45")
        add("timezoneid", "Europe/Brussels") // wordt "Europe\/Brussels" door escapeJson (forward slash wordt ge-escaped)
        add("bron", "4")
        add("_id", "")
        add("tellingid", "")
        add("telpostid", telpostId)
        add("begintijd", beginSec.toString())
        add("eindtijd", eindSec.toString())
        add("tellers", form.tellersNaam)
        add("weer", weerOpmerking)
        add("windrichting", windrichtingCode)
        add("windkracht", windkrachtStr)
        add("temperatuur", tempStr)
        add("bewolking", bewolkingStr)
        add("bewolkinghoogte", "")
        add("neerslag", neerslagCode)
        add("duurneerslag", "")
        add("zicht", zichtMetersStr)
        add("tellersactief", "")
        add("tellersaanwezig", "")
        add("typetelling", typeTellingCode)
        add("metersnet", "")
        add("geluid", "")
        add("opmerkingen", form.opmerkingen ?: "")
        add("onlineid", onlineId)
        add("HYDRO", "")
        add("hpa", hpaStr)
        add("equipment", "")
        add("uuid", uuid)
        add("uploadtijdstip", "")
        add("nrec", nrec)
        add("nsoort", nsoort)

        // data-array als geldige JSON, dus zonder quotes
        sb.append(',').append('"').append("data").append('"').append(':').append(dataArrayJson)

        sb.append('}').append(']')
        return sb.toString()
    }

    // ======================
    // Mapping helpers
    // ======================

    /** Map spinner label -> API code voor typetelling. */
    private fun mapTypeTelling(label: String): String = when (label.trim().lowercase(Locale.ROOT)) {
        "alle soorten" -> "all"
        "zeetrek" -> "sea"
        "ooievaars en roofvogels" -> "raptor"
        "landtrek op kusttelpost" -> "coastalvismig"
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

    /**
     * Map windkracht-spinner (“var”, “<1bf”, “1bf”…“11bf”, “>11bf”) → cijfer-string ("0".."12").
     * Bovengrens >11bf → 12, var en <1bf → 0.
     */
    private fun mapWindkrachtToIntString(label: String?): String {
        if (label.isNullOrBlank()) return ""
        val v = label.trim().lowercase(Locale.ROOT)
        return when {
            v == "var" -> "0"
            v == "<1bf" -> "0"
            v == ">11bf" -> "12"
            v.endsWith("bf") -> {
                val n = v.removeSuffix("bf").toIntOrNull()
                if (n == null) "" else n.coerceIn(0, 12).toString()
            }
            else -> ""
        }
    }

    /** Map bewolking spinner (“0/8”..“8/8”) → enkel cijfer "0".."8" als string. */
    private fun mapBewolkingAchtsten(label: String?): String {
        if (label.isNullOrBlank()) return ""
        val n = label.substringBefore('/').trim().toIntOrNull() ?: return ""
        return n.coerceIn(0, 8).toString()
    }

    // ======================
    // JSON escape helper
    // ======================

    /**
     * Minimale JSON escaping:
     * - Quotes, backslashes en control chars
     * - **Forward slash** wordt ook ge-escaped → "Europe\/Brussels" (zoals gevraagd)
     */
    private fun escapeJson(input: String): String {
        var needs = false
        for (ch in input) {
            if (ch == '"' || ch == '\\' || ch == '/' || ch < ' ') { // '/' extra
                needs = true; break
            }
        }
        if (!needs) return input
        val out = StringBuilder(input.length + 8)
        for (ch in input) {
            when (ch) {
                '"'  -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '/'  -> out.append("\\/") // optioneel volgens JSON-spec; hier expliciet aangezet
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> {
                    if (ch < ' ') {
                        out.append("\\u")
                        val code = ch.code
                        out.append(((code shr 12) and 0xF).toHex())
                        out.append(((code shr 8) and 0xF).toHex())
                        out.append(((code shr 4) and 0xF).toHex())
                        out.append((code and 0xF).toHex())
                    } else {
                        out.append(ch)
                    }
                }
            }
        }
        return out.toString()
    }

    private fun Int.toHex(): Char = "0123456789abcdef"[this]
}