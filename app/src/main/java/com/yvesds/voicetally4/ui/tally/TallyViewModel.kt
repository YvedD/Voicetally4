package com.yvesds.voicetally4.ui.tally

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yvesds.voicetally4.utils.io.StorageUtils
import com.yvesds.voicetally4.utils.settings.SettingsKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Houdt actieve soorten, totalen en de spraaklog bij. Schrijft debounced autosaves.
 * Nav/Activity-scoped houden zodat log & state niet verdwijnen bij navigatie.
 */
class TallyViewModel(app: Application) : AndroidViewModel(app) {

    data class UiState(
        val listening: Boolean = false,
        val speechLog: List<String> = emptyList(),
        val totals: Map<String, Int> = emptyMap(),       // soortId -> count
        val activeSpecies: Set<String> = emptySet(),     // soortId in telling
        val speciesNames: Map<String, String> = emptyMap() // soortId -> displayName
    )

    private val state = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = state

    private val totalsMutable = ConcurrentHashMap<String, Int>()
    private val speciesNames = ConcurrentHashMap<String, String>()
    private val activeSet = ConcurrentHashMap.newKeySet<String>()

    private val appCtx: Context get() = getApplication<Application>().applicationContext
    private val prefs: SharedPreferences =
        appCtx.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
    private val uploadPrefs: SharedPreferences =
        appCtx.getSharedPreferences(SettingsKeys.UPLOAD_PREFS, Context.MODE_PRIVATE)

    // Autosave
    private var saveJob: Job? = null
    private val saveDebounceMs = 600L

    fun setListening(listening: Boolean) {
        state.value = state.value.copy(listening = listening)
    }

    fun appendSpeechLog(line: String) {
        val newLog = (state.value.speechLog + line).takeLast(200)
        state.value = state.value.copy(speechLog = newLog)
    }

    fun addActive(speciesId: String, displayName: String) {
        activeSet.add(speciesId)
        speciesNames[speciesId] = displayName
        publishState()
    }

    fun addActiveMany(items: List<Pair<String, String>>) {
        items.forEach { (id, name) ->
            activeSet.add(id)
            speciesNames[id] = name
        }
        publishState()
    }

    fun book(speciesId: String, delta: Int) {
        if (delta == 0) return
        val newVal = (totalsMutable[speciesId] ?: 0) + delta
        totalsMutable[speciesId] = newVal.coerceAtLeast(0)
        publishState()
        scheduleSave()
    }

    fun reset(speciesId: String) {
        totalsMutable.remove(speciesId)
        publishState()
        scheduleSave()
    }

    fun acceptNewSpeciesAndBook(items: List<Triple<String, String, Int>>) {
        // items: (id, display, amount)
        items.forEach { (id, name, amount) ->
            activeSet.add(id)
            speciesNames[id] = name
            val newVal = (totalsMutable[id] ?: 0) + amount
            totalsMutable[id] = newVal
        }
        publishState()
        scheduleSave()
    }

    private fun publishState() {
        state.value = UiState(
            listening = state.value.listening,
            speechLog = state.value.speechLog,
            totals = HashMap(totalsMutable),
            activeSpecies = HashSet(activeSet),
            speciesNames = HashMap(speciesNames)
        )
    }

    /** Debounced autosave van tellingen.json + append events.log. */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(saveDebounceMs)
            saveTotalsJson()
        }
    }

    private fun saveTotalsJson() {
        val dir = StorageUtils.getPublicAppDir(appCtx, SettingsKeys.DIR_TELLINGEN)
        if (!dir.exists()) dir.mkdirs()

        val (file, sessionId) = currentTellingFile(dir)
        // JSON object: tijdstip, onlineid (indien beschikbaar), totals array met {id, name, count}
        val arr = JSONArray()
        for ((id, count) in totalsMutable) {
            val name = speciesNames[id] ?: id
            arr.put(JSONObject().apply {
                put("id", id)
                put("name", name)
                put("count", count)
            })
        }
        val root = JSONObject().apply {
            put("sessionId", sessionId)
            put("onlineid", getOnlineIdOrNull())
            put("updated", DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
            put("totals", arr)
        }
        file.writeText(root.toString())
    }

    /** Append 1 regel naar events.log in dezelfde folder (ndjson). */
    fun appendEventLine(obj: JSONObject) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = StorageUtils.getPublicAppDir(appCtx, SettingsKeys.DIR_TELLINGEN)
            if (!dir.exists()) dir.mkdirs()
            val (file, _) = currentTellingFile(dir)
            val log = File(dir, file.nameWithoutExtension + "_events.log")
            log.appendText(obj.toString() + "\n")
        }
    }

    private fun getOnlineIdOrNull(): String? =
        uploadPrefs.getString(SettingsKeys.KEY_LAST_ONLINE_ID, null)

    private fun currentTellingFile(dir: File): Pair<File, String> {
        val online = getOnlineIdOrNull()
        val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val sessionId = (online ?: "offline") + "_" + ts
        val file = File(dir, "$sessionId.json")
        return file to sessionId
    }
}
