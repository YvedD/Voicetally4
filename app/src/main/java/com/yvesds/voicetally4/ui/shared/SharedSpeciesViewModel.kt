package com.yvesds.voicetally4.ui.shared

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Draagt de selectie van soorten (canonicals) en optionele display-namen
 * (canonical -> tileName) tussen schermen (SoortSelectie -> Tally).
 *
 * - API en gedrag blijven zoals in jouw project.
 * - Geen directe afhankelijkheid van UnifiedAliasStore hier: dit is puur state.
 */
class SharedSpeciesViewModel : ViewModel() {

    // Geselecteerde canonicals in volgorde van keuze
    private val _selectedCanonicals = MutableStateFlow<List<String>>(emptyList())
    val selectedCanonicals: StateFlow<List<String>> = _selectedCanonicals.asStateFlow()

    // Optionele mapping: canonical -> displayName (tile name)
    // (Tally kan ook rechtstreeks uit UnifiedAliasStore de displayName halen,
    //  maar we behouden deze map om backwards compat en UI stabiliteit te garanderen.)
    private val _displayNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val displayNames: StateFlow<Map<String, String>> = _displayNames.asStateFlow()

    /** Zet de volledige set canonicals die gekozen werden. */
    fun setSelectedSpecies(canonicals: List<String>) {
        _selectedCanonicals.value = canonicals
    }

    /** Zet de mapping (canonical -> tileName) voor de huidige selectie. */
    fun setDisplayNames(map: Map<String, String>) {
        _displayNames.value = map
    }

    /** Leegmaken (optioneel). */
    fun clear() {
        _selectedCanonicals.value = emptyList()
        _displayNames.value = emptyMap()
    }
}
