package com.yvesds.voicetally4.ui.domein

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yvesds.voicetally4.ui.data.SoortAlias
import com.yvesds.voicetally4.ui.data.UnifiedAliasStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gebruikt UnifiedAliasStore als enige bron.
 * Publieke API & UiState blijven gelijk voor het scherm.
 */
class SoortSelectieViewModel(
    app: Application
) : AndroidViewModel(app) {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(val items: List<SoortAlias>) : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    // Geselecteerde tegels bijhouden per tileName (displayname)
    private val selected = LinkedHashSet<String>()

    fun isSelected(tileName: String): Boolean = selected.contains(tileName)

    fun toggleSelection(tileName: String) {
        if (!selected.add(tileName)) selected.remove(tileName)
    }

    fun forceReload() {
        loadAliases()
    }

    fun loadAliases() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    UnifiedAliasStore.ensureReady(getApplication())
                }
                val tiles = UnifiedAliasStore.allTiles().map {
                    SoortAlias(
                        soortId = it.speciesId,
                        canonical = it.canonical,
                        tileName = it.displayName,
                        aliases = emptyList() // voor selectie niet nodig
                    )
                }
                _uiState.value = UiState.Success(tiles)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Aliassen konden niet geladen worden.")
            }
        }
    }

    /** Voor Tally: lijst canonicals en display map (canonical→tileName). */
    fun setSelection(canonicals: List<String>, displayMap: Map<String, String>) {
        // Niet gebruikt hier; API behouden als je die elders wil aanroepen
    }
}
