package com.yvesds.voicetally4.ui.tally

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yvesds.voicetally4.ui.adapters.TallyItem
import com.yvesds.voicetally4.ui.data.UnifiedAliasStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TallyViewModel (light):
 *
 * - Één bron: UnifiedAliasStore (speciesId ↔ displayName/canonical).
 * - Houdt tellingen per *canonical* bij (compatibel met jouw bestaande flow).
 * - Public API (alleen wat we nu nodig hebben):
 *      setSelection(canonicals, displayMap)
 *      getCount(canonical)
 *      setCount(canonical, value)
 * - items: StateFlow<List<TallyItem>> voor de adapter (tile weergave).
 *
 * Geen increment/decrement/reset meer — we werken via detail-popup (setCount).
 */
class TallyViewModel(app: Application) : AndroidViewModel(app) {

    private data class Row(
        val canonical: String,
        var speciesId: String,   // uit store (fallback: canonical)
        var displayName: String, // tileName; primair store, fallback displayMap/canonical
        var count: Int
    )

    private val rowsByCanonical = LinkedHashMap<String, Row>(64)

    private val _items = MutableStateFlow<List<TallyItem>>(emptyList())
    val items: StateFlow<List<TallyItem>> = _items

    /**
     * Pas de (nieuwe) selectie toe.
     * - Bestaande counts blijven behouden voor overlappende items.
     * - DisplayName/Id worden ververst vanuit de store.
     */
    fun setSelection(canonicals: List<String>, displayMap: Map<String, String>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                UnifiedAliasStore.ensureReady(getApplication())
            }

            val keepSet = canonicals.toHashSet()
            val kept = LinkedHashMap<String, Row>(canonicals.size)

            // behoud bestaande die nog in de selectie zitten
            for ((canon, row) in rowsByCanonical) {
                if (canon in keepSet) kept[canon] = row
            }

            // voeg nieuw toe / refresh naam & id
            for (canon in canonicals) {
                val existing = kept[canon]
                if (existing == null) {
                    val (speciesId, displayName) = resolveIdAndName(canon, displayMap)
                    kept[canon] = Row(
                        canonical = canon,
                        speciesId = speciesId,
                        displayName = displayName,
                        count = 0
                    )
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

    /** Huidig aantal voor canonical (of 0 als onbekend). */
    fun getCount(canonical: String): Int {
        return rowsByCanonical[canonical]?.count ?: 0
    }

    /** Zet exact aantal voor canonical (min 0). */
    fun setCount(canonical: String, value: Int) {
        val row = rowsByCanonical[canonical] ?: return
        row.count = value.coerceAtLeast(0)
        publishItems()
    }

    // -------------------- helpers --------------------

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
            out += TallyItem(
                speciesId = r.speciesId,
                name = r.displayName,
                count = r.count
            )
        }
        _items.value = out
    }
}
