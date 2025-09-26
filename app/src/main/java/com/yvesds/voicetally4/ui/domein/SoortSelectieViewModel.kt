package com.yvesds.voicetally4.ui.domein

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yvesds.voicetally4.ui.data.UnifiedAliasStore
import com.yvesds.voicetally4.utils.io.StorageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SoortSelectieViewModel @Inject constructor(
    @param:ApplicationContext // ← behoud huidig gedrag; geen toekomstige target-warnings
    private val appContext: Context
) : ViewModel() {

    sealed class UiState {
        data class Loading(val message: String) : UiState()
        data class Success(val items: List<AliasItem>) : UiState()
        data class Error(val message: String) : UiState()
    }

    data class AliasItem(
        val soortId: String,
        val canonical: String,
        val tileName: String,
        val aliases: List<String>
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading("Start…"))
    val uiState: StateFlow<UiState> = _uiState

    private val selectedTiles = LinkedHashSet<String>() // keys = tileName
    fun isSelected(tileName: String): Boolean = selectedTiles.contains(tileName)
    fun toggleSelection(tileName: String) {
        if (!selectedTiles.add(tileName)) selectedTiles.remove(tileName)
    }

    private var started = false

    fun loadAliases() {
        if (started) return
        started = true

        viewModelScope.launch {
            _uiState.value = UiState.Loading("Soorten inlezen…")

            val list = withContext(Dispatchers.IO) {
                UnifiedAliasStore.loadSelectionList(appContext)
            }

            if (list.isNotEmpty()) {
                val items = list.map {
                    AliasItem(
                        soortId = it.soortId,
                        canonical = it.canonical,
                        tileName = it.display,
                        aliases = it.aliases
                    )
                }
                _uiState.value = UiState.Success(items)
                return@launch
            }

            val assetsDir: File = StorageUtils.getPublicAppDir(appContext, "assets")
            val f = File(assetsDir, "aliasmapping.csv")
            _uiState.value = UiState.Error(
                if (!f.exists()) "aliasmapping.csv niet gevonden in Documents/VoiceTally4/assets/"
                else "Kon geen geldige aliasdata laden."
            )
        }
    }

    /** Forceer herladen (gebruikt na SAF-kopie). */
    fun forceReload() {
        started = false
        loadAliases()
    }
}
