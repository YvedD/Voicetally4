package com.yvesds.voicetally4.ui.shared

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Gedeelde state tussen SoortSelectieScherm en TallyScherm.
 * - selectedCanonicals: lijst van canonical sleutels (vb. "anas_platyrhynchos")
 * - displayNames: mapping canonical -> tile/display name (wat je op de tegels wil tonen)
 */
class SharedSpeciesViewModel : ViewModel() {

    private val _selectedCanonicals = MutableStateFlow<List<String>>(emptyList())
    val selectedCanonicals: StateFlow<List<String>> = _selectedCanonicals

    private val _displayNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val displayNames: StateFlow<Map<String, String>> = _displayNames

    /** Stel de huidige selectie in (canonical keys). */
    fun setSelectedSpecies(canonicals: List<String>) {
        _selectedCanonicals.value = canonicals.distinct()
    }

    /** Stel de namen in die je op de tegels wil tonen. */
    fun setDisplayNames(map: Map<String, String>) {
        _displayNames.value = map
    }

    fun clear() {
        _selectedCanonicals.value = emptyList()
        _displayNames.value = emptyMap()
    }
}
