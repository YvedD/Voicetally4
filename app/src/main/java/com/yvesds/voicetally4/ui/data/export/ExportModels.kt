package com.yvesds.voicetally4.data.export

import org.json.JSONArray
import org.json.JSONObject

/**
 * Data-klassen voor upload naar /api/counts_save.
 * We serialiseren bewust handmatig (geen extra libs vereist).
 */

data class SessionExport(
    val externid: String,
    val timezoneid: String,
    val bron: String,
    val idLocal: String,          // "_id" (leeg laten)
    val tellingIdLocal: String,   // "tellingid" (leeg laten)
    val telpostid: String,
    val begintijd: String,        // epoch sec als string
    val eindtijd: String,         // epoch sec als string (dummy = +60s)
    val tellers: String,
    val weer: String,
    val windrichting: String,
    val windkracht: String,
    val temperatuur: String,
    val bewolking: String,
    val bewolkinghoogte: String,
    val neerslag: String,
    val duurneerslag: String,
    val zicht: String,
    val tellersactief: String,
    val tellersaanwezig: String,
    val typetelling: String,      // ⚠️ servercode (via CodesRepository)
    val metersnet: String,
    val geluid: String,
    val opmerkingen: String,
    val onlineid: String,
    val hydro: String,
    val hpa: String,
    val equipment: String,
    val uuid: String,
    val uploadtijdstip: String,
    val nrec: String,
    val nsoort: String,
    val dataArray: JSONArray
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("externid", externid)
        put("timezoneid", timezoneid)
        put("bron", bron)
        put("_id", idLocal)
        put("tellingid", tellingIdLocal)
        put("telpostid", telpostid)
        put("begintijd", begintijd)
        put("eindtijd", eindtijd)
        put("tellers", tellers)
        put("weer", weer)
        put("windrichting", windrichting)
        put("windkracht", windkracht)
        put("temperatuur", temperatuur)
        put("bewolking", bewolking)
        put("bewolkinghoogte", bewolkinghoogte)
        put("neerslag", neerslag)
        put("duurneerslag", duurneerslag)
        put("zicht", zicht)
        put("tellersactief", tellersactief)
        put("tellersaanwezig", tellersaanwezig)
        put("typetelling", typetelling)
        put("metersnet", metersnet)
        put("geluid", geluid)
        put("opmerkingen", opmerkingen)
        put("onlineid", onlineid)
        put("HYDRO", hydro)
        put("hpa", hpa)
        put("equipment", equipment)
        put("uuid", uuid)
        put("uploadtijdstip", uploadtijdstip)
        put("nrec", nrec)
        put("nsoort", nsoort)
        put("data", dataArray)
    }
}

data class ExportPayload(
    val sessions: List<SessionExport>
) {
    fun toJsonArray(): JSONArray = JSONArray().apply { sessions.forEach { put(it.toJsonObject()) } }
    fun toJsonString(pretty: Boolean = true): String =
        if (pretty) toJsonArray().toString(2) else toJsonArray().toString()
}
