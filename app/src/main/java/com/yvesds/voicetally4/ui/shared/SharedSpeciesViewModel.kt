package com.yvesds.voicetally4.shared

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Activity-scoped gedeelde state voor selectie, tellers, display-namen en spraaklog.
 *
 * - selectedSpecies: de canonieke soortnamen (bijv. "aalscholver")
 * - displayNames:   mapping van canonieke naam -> weergavenaam (kolom 2 aliasmapping.csv)
 * - tallyMap:       aantallen per canonieke soort
 * - speechLogs:     simpele lijst met logregels
 */
@HiltViewModel
class SharedSpeciesViewModel @Inject constructor() : ViewModel() {

    private val _selectedSpecies = MutableStateFlow<LinkedHashSet<String>>(linkedSetOf())
    val selectedSpecies: StateFlow<Set<String>> = _selectedSpecies.asStateFlow()

    // Optioneel: aliassen van de geselecteerde soorten
    private val _selectedAliases = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val selectedAliases: StateFlow<Map<String, List<String>>> = _selectedAliases.asStateFlow()

    // NIEUW: weergavenamen (kolom 2 van aliasmapping.csv) per canonieke soort
    private val _displayNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val displayNames: StateFlow<Map<String, String>> = _displayNames.asStateFlow()

    private val _tallyMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val tallyMap: StateFlow<Map<String, Int>> = _tallyMap.asStateFlow()

    private val _speechLogs = MutableStateFlow<List<String>>(emptyList())
    val speechLogs: StateFlow<List<String>> = _speechLogs.asStateFlow()

    /** Volledige set geselecteerde soorten plaatsen (behoud counts waar mogelijk). */
    fun setSelectedSpecies(species: Collection<String>) {
        val newOrdered = LinkedHashSet<String>().apply { addAll(species) }
        _selectedSpecies.value = newOrdered

        // Re-map tally: behoud bestaande waarden, init 0 voor nieuwe soorten.
        val current = _tallyMap.value
        val next = buildMap {
            for (s in newOrdered) put(s, current[s] ?: 0)
        }
        _tallyMap.value = next
    }

    /** Optioneel: aliassen van de geselecteerde soorten. */
    fun setSelectedAliases(map: Map<String, List<String>>) {
        _selectedAliases.value = map
    }

    /** NIEUW: display-namen (kolom 2 aliasmapping.csv) per canonieke soort. */
    fun setDisplayNames(map: Map<String, String>) {
        _displayNames.value = map
    }

    /** Handige helper: geef weergavenaam als beschikbaar, anders canonieke naam. */
    fun displayNameOf(speciesCanonical: String): String {
        return _displayNames.value[speciesCanonical] ?: speciesCanonical
    }

    fun increment(species: String, delta: Int = 1) {
        updateCount(species, (tallyMap.value[species] ?: 0) + delta)
    }

    fun decrement(species: String, delta: Int = 1) {
        updateCount(species, (tallyMap.value[species] ?: 0) - delta)
    }

    fun reset(species: String) {
        updateCount(species, 0)
    }

    fun setCount(species: String, count: Int) {
        updateCount(species, count)
    }

    private fun updateCount(species: String, newCount: Int) {
        val current = _tallyMap.value
        if (!current.containsKey(species)) return
        _tallyMap.value = current.toMutableMap().apply { put(species, newCount.coerceAtLeast(0)) }
    }

    fun appendLog(line: String) {
        _speechLogs.value = _speechLogs.value + line
    }

    /** Volledige reset (optioneel te gebruiken wanneer sessie wisselt). */
    fun clearAll() {
        _selectedSpecies.value = linkedSetOf()
        _selectedAliases.value = emptyMap()
        _displayNames.value = emptyMap()
        _tallyMap.value = emptyMap()
        _speechLogs.value = emptyList()
    }
}
