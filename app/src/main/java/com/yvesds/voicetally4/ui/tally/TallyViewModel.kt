package com.yvesds.voicetally4.ui.tally

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * No-arg ViewModel (lifecycle-correct, bewaart state over rotaties).
 * - Ontvangt selectie & display-namen via setSelection(...) vanuit het Fragment.
 * - Beheert counts per canonical en expose't lijstitems met displayName (tile-name).
 */
class TallyViewModel : ViewModel() {

    data class TallyItem(
        val canonical: String,
        val displayName: String,
        val count: Int
    )

    private val selectedCanonicals = MutableStateFlow<List<String>>(emptyList())
    private val displayNames = MutableStateFlow<Map<String, String>>(emptyMap())
    private val counts = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Lijst voor de adapter (volgorde = volgorde van selectie). */
    val items: StateFlow<List<TallyItem>> =
        combine(selectedCanonicals, displayNames, counts) { canonicals, displayMap, curCounts ->
            canonicals.map { canonical ->
                val name = displayMap[canonical] ?: canonical
                val c = curCounts[canonical] ?: 0
                TallyItem(canonical = canonical, displayName = name, count = c)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Wordt door het Fragment aangeroepen wanneer SharedSpeciesViewModel verandert. */
    fun setSelection(canonicals: List<String>, displayMap: Map<String, String>) {
        selectedCanonicals.value = canonicals.distinct()
        displayNames.value = displayMap
        // Zorg dat verdwenen soorten niet blijven hangen in counts
        val allowed = selectedCanonicals.value.toSet()
        val cur = counts.value
        if (cur.keys.any { it !in allowed }) {
            counts.value = cur.filterKeys { it in allowed }
        }
        // Zorg dat nieuwe keys een startwaarde hebben (0) zonder bestaande waarden te overschrijven
        if (allowed.isNotEmpty()) {
            val next = cur.toMutableMap()
            for (c in allowed) if (c !in next) next[c] = 0
            counts.value = next
        }
    }

    fun increment(canonical: String) = viewModelScope.launch {
        val cur = counts.value
        val next = (cur[canonical] ?: 0) + 1
        counts.value = cur.toMutableMap().apply { put(canonical, next) }
    }

    fun decrement(canonical: String) = viewModelScope.launch {
        val cur = counts.value
        val next = (cur[canonical] ?: 0) - 1
        counts.value = cur.toMutableMap().apply { put(canonical, next.coerceAtLeast(0)) }
    }

    fun reset(canonical: String) = viewModelScope.launch {
        val cur = counts.value
        if (cur.containsKey(canonical)) {
            counts.value = cur.toMutableMap().apply { put(canonical, 0) }
        }
    }

    /** Door dialog gebruikt om rechtstreeks te zetten. */
    fun setCount(canonical: String, count: Int) = viewModelScope.launch {
        val cur = counts.value
        if (cur.containsKey(canonical)) {
            counts.value = cur.toMutableMap().apply { put(canonical, count.coerceAtLeast(0)) }
        } else {
            // Als canonical nog niet bestond maar nu gezet wordt, initialiseer dan.
            counts.value = cur.toMutableMap().apply { put(canonical, count.coerceAtLeast(0)) }
        }
    }

    /** Handig voor dialog om huidige waarde op te halen. */
    fun getCount(canonical: String): Int = counts.value[canonical] ?: 0

    fun clearAll() = viewModelScope.launch {
        counts.value = emptyMap()
    }
}
