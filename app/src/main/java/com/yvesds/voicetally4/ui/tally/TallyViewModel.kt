package com.yvesds.voicetally4.ui.tally

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yvesds.voicetally4.ui.adapters.TallyItem
import com.yvesds.voicetally4.ui.data.ObservationRecord
import com.yvesds.voicetally4.ui.data.UnifiedAliasStore
import com.yvesds.voicetally4.utils.io.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * TallyViewModel
 * - Houdt tiles bij (species).
 * - Beheert ObservationRecords + counters nrec/nsoort.
 * - Zorgt dat Documents/VoiceTally4/counts bestaat (voor later persist).
 */
class TallyViewModel(app: Application) : AndroidViewModel(app) {

    // ---- Tiles ----
    private data class Row(
        val canonical: String,
        var speciesId: String,
        var displayName: String,
        var count: Int
    )
    private val rowsByCanonical = LinkedHashMap<String, Row>(64)
    private val _items = MutableStateFlow<List<TallyItem>>(emptyList())
    val items: StateFlow<List<TallyItem>> = _items

    fun setSelection(canonicals: List<String>, displayMap: Map<String, String>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { UnifiedAliasStore.ensureReady(getApplication()) }

            val keepSet = canonicals.toHashSet()
            val kept = LinkedHashMap<String, Row>(canonicals.size)

            for ((canon, row) in rowsByCanonical) {
                if (canon in keepSet) kept[canon] = row
            }
            for (canon in canonicals) {
                val existing = kept[canon]
                if (existing == null) {
                    val (speciesId, displayName) = resolveIdAndName(canon, displayMap)
                    kept[canon] = Row(canon, speciesId, displayName, 0)
                } else {
                    val (speciesId, displayName) = resolveIdAndName(canon, displayMap)
                    existing.speciesId = speciesId
                    existing.displayName = displayName
                }
            }
            rowsByCanonical.clear()
            rowsByCanonical.putAll(kept)
            publishItems()
        }
    }

    fun getCount(canonical: String): Int = rowsByCanonical[canonical]?.count ?: 0

    fun setCount(canonical: String, value: Int) {
        val row = rowsByCanonical[canonical] ?: return
        row.count = value.coerceAtLeast(0)
        publishItems()
    }

    private fun resolveIdAndName(
        canonical: String,
        displayMap: Map<String, String>
    ): Pair<String, String> {
        val tile = UnifiedAliasStore.allTiles().firstOrNull { it.canonical == canonical }
        val speciesId = tile?.speciesId ?: canonical
        val displayName = tile?.displayName ?: (displayMap[canonical] ?: canonical)
        return speciesId to displayName
    }

    private fun publishItems() {
        if (rowsByCanonical.isEmpty()) {
            _items.value = emptyList()
            return
        }
        val out = ArrayList<TallyItem>(rowsByCanonical.size)
        for ((_, r) in rowsByCanonical) {
            out += TallyItem(speciesId = r.speciesId, name = r.displayName, count = r.count)
        }
        _items.value = out
    }

    // ---- ObservationRecords ----
    private val _records = MutableStateFlow<List<ObservationRecord>>(emptyList())
    val records: StateFlow<List<ObservationRecord>> = _records

    private var nextInternalId: Int = 1
    private var tellingId: String = ""

    private val _nrec = MutableStateFlow("0")
    val nrec: StateFlow<String> = _nrec

    private val _nsoort = MutableStateFlow("0")
    val nsoort: StateFlow<String> = _nsoort

    init {
        // Zorg dat counts-submap bestaat
        StorageUtils.getPublicAppDir(getApplication(), "counts")
    }

    fun getOrCreateTellingId(): String {
        if (tellingId.isBlank()) {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
            tellingId = sdf.format(System.currentTimeMillis())
        }
        return tellingId
    }

    private fun nextRecordId(): String = (nextInternalId++).toString()

    fun addRecord(rec: ObservationRecord) {
        val id = if (rec._id.isBlank()) nextRecordId() else rec._id
        val tId = getOrCreateTellingId()
        val newRec = rec.copy(_id = id, tellingid = tId)

        val newList = _records.value.toMutableList().apply { add(newRec) }
        _records.value = newList
        updateMetadata(newList)
    }

    fun updateRecord(rec: ObservationRecord) {
        if (rec._id.isBlank()) return
        val list = _records.value.toMutableList()
        val idx = list.indexOfFirst { it._id == rec._id }
        if (idx >= 0) {
            val tId = if (rec.tellingid.isBlank()) getOrCreateTellingId() else rec.tellingid
            list[idx] = rec.copy(tellingid = tId)
            _records.value = list
            updateMetadata(list)
        }
    }

    fun deleteRecordById(id: String): Boolean {
        val list = _records.value.toMutableList()
        val removed = list.removeAll { it._id == id }
        if (removed) {
            _records.value = list
            updateMetadata(list)
        }
        return removed
    }

    private fun updateMetadata(list: List<ObservationRecord>) {
        _nrec.value = list.size.toString()
        _nsoort.value = list.map { it.soortid }.toSet().size.toString()
    }

    // tijd helpers
    fun epochSecondsForToday(hour24: Int, minute: Int): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.HOUR_OF_DAY, hour24.coerceIn(0, 23))
        cal.set(Calendar.MINUTE, minute.coerceIn(0, 59))
        return (cal.timeInMillis / 1000L).toString()
    }

    fun nowFormatted(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return sdf.format(System.currentTimeMillis())
    }
}
