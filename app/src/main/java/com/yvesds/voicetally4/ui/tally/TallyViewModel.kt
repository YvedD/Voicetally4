package com.yvesds.voicetally4.ui.tally

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yvesds.voicetally4.shared.SharedSpeciesViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Compatibele, veilige "brug"-ViewModel voor Tally.
 *
 * - Werkt standalone (eigen StateFlows) zónder verplicht shared VM.
 * - Kan optioneel synchroniseren met SharedSpeciesViewModel via [attachShared].
 * - Publieke API spiegelt de belangrijkste operaties (increment/decrement/reset/setCount/appendLog).
 *
 * Gebruik:
 *   val vm: TallyViewModel by viewModels()
 *   vm.attachShared(sharedVm) // optioneel; koppelt flows + delegatie
 *
 * Als attachShared() NIET wordt aangeroepen:
 *   - werkt TallyViewModel op eigen lokale state (handig in tests of legacy code).
 * Als attachShared() WEL wordt aangeroepen:
 *   - leest hij doorlopend mee met de shared VM en delegeert mutaties daarnaar.
 */
@HiltViewModel
class TallyViewModel @Inject constructor() : ViewModel() {

    // --- Lokale (fallback) state ---
    private val _selectedSpecies = MutableStateFlow<LinkedHashSet<String>>(linkedSetOf())
    val selectedSpecies: StateFlow<Set<String>> = _selectedSpecies.asStateFlow()

    private val _tallyMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val tallyMap: StateFlow<Map<String, Int>> = _tallyMap.asStateFlow()

    private val _speechLogs = MutableStateFlow<List<String>>(emptyList())
    val speechLogs: StateFlow<List<String>> = _speechLogs.asStateFlow()

    // --- Koppeling naar Shared VM (optioneel) ---
    private val isAttached = AtomicBoolean(false)
    private var shared: SharedSpeciesViewModel? = null
    private var syncJob: Job? = null

    /**
     * Koppel aan de gedeelde VM. Idempotent: extra aanroepen doen niets.
     * Synchroniseert de lokale flows en delegeert mutatie-calls naar shared.
     */
    fun attachShared(sharedVm: SharedSpeciesViewModel) {
        if (!isAttached.compareAndSet(false, true)) return
        shared = sharedVm

        // Initieel: neem huidige waarden over
        _selectedSpecies.value = LinkedHashSet(sharedVm.selectedSpecies.value)
        _tallyMap.value = sharedVm.tallyMap.value
        _speechLogs.value = sharedVm.speechLogs.value

        // Doorlopende sync
        syncJob = viewModelScope.launch {
            // Elk collect op aparte coroutine om onafhankelijk te updaten
            launch {
                sharedVm.selectedSpecies.collect { set ->
                    _selectedSpecies.value = LinkedHashSet(set)
                }
            }
            launch {
                sharedVm.tallyMap.collect { map ->
                    _tallyMap.value = map
                }
            }
            launch {
                sharedVm.speechLogs.collect { logs ->
                    _speechLogs.value = logs
                }
            }
        }
    }

    // --- Mutaties: delegeren naar shared (indien gekoppeld), anders lokaal toepassen ---

    fun setSelectedSpecies(species: Collection<String>) {
        val s = shared
        if (s != null && isAttached.get()) {
            s.setSelectedSpecies(species)
        } else {
            val newOrdered = LinkedHashSet<String>().apply { addAll(species) }
            _selectedSpecies.value = newOrdered
            val current = _tallyMap.value
            _tallyMap.value = buildMap {
                for (sp in newOrdered) put(sp, current[sp] ?: 0)
            }
        }
    }

    fun increment(species: String, delta: Int = 1) {
        val s = shared
        if (s != null && isAttached.get()) {
            s.increment(species, delta)
        } else {
            updateLocalCount(species) { it + delta }
        }
    }

    fun decrement(species: String, delta: Int = 1) {
        val s = shared
        if (s != null && isAttached.get()) {
            s.decrement(species, delta)
        } else {
            updateLocalCount(species) { (it - delta).coerceAtLeast(0) }
        }
    }

    fun reset(species: String) {
        val s = shared
        if (s != null && isAttached.get()) {
            s.reset(species)
        } else {
            updateLocalCount(species) { 0 }
        }
    }

    fun setCount(species: String, count: Int) {
        val s = shared
        if (s != null && isAttached.get()) {
            s.setCount(species, count)
        } else {
            updateLocalCount(species) { count.coerceAtLeast(0) }
        }
    }

    fun appendLog(line: String) {
        val s = shared
        if (s != null && isAttached.get()) {
            s.appendLog(line)
        } else {
            _speechLogs.value = _speechLogs.value + line
        }
    }

    fun clearAll() {
        val s = shared
        if (s != null && isAttached.get()) {
            s.clearAll()
        } else {
            _selectedSpecies.value = linkedSetOf()
            _tallyMap.value = emptyMap()
            _speechLogs.value = emptyList()
        }
    }

    // --- Helpers ---

    private fun updateLocalCount(species: String, transform: (Int) -> Int) {
        val current = _tallyMap.value
        if (!current.containsKey(species)) return
        val new = transform(current[species] ?: 0)
        _tallyMap.value = current.toMutableMap().apply { put(species, new) }
    }

    override fun onCleared() {
        super.onCleared()
        syncJob?.cancel()
    }
}
