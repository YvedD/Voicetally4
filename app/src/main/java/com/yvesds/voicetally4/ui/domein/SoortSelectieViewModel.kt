package com.yvesds.voicetally4.ui.domein

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yvesds.voicetally4.ui.core.SetupManager
import com.yvesds.voicetally4.ui.data.SoortAlias
import com.yvesds.voicetally4.utils.io.StorageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.charset.Charset
import javax.inject.Inject

@HiltViewModel
class SoortSelectieViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context, // target only the parameter (avoids K2 warning)
    private val setupManager: SetupManager
) : ViewModel() {

    // ---------- UI state ----------
    sealed class UiState {
        data class Loading(val message: String) : UiState()
        data class Success(val items: List<SoortAlias>) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading("Soorten inlezen…"))
    val uiState: StateFlow<UiState> = _uiState

    // Geselecteerde tiles (keys = tileName)
    private val _selected = MutableStateFlow<LinkedHashSet<String>>(linkedSetOf())
    val selected: StateFlow<LinkedHashSet<String>> = _selected

    @Volatile
    private var started = false

    fun loadAliases() {
        if (started) return
        started = true

        viewModelScope.launch {
            _uiState.value = UiState.Loading("Soorten inlezen…")

            val csvDoc = withContext(Dispatchers.IO) { findCsvDocument() }
            if (csvDoc == null) {
                _uiState.value = UiState.Error("aliasmapping.csv niet gevonden in Documents/VoiceTally4/assets/")
                return@launch
            }

            val csvSize = csvDoc.length()
            val csvMtime = csvDoc.lastModified()
            val binDir: File = StorageUtils.getPublicAppDir(appContext, "binaries")
            val binFile = File(binDir, "aliases.bin")

            // 1) Probeer binaire cache
            val cached = withContext(Dispatchers.IO) { readBinaryIfFresh(binFile, csvSize, csvMtime) }
            if (cached != null) {
                _uiState.value = UiState.Success(cached)
                return@launch
            }

            // 2) Parse CSV
            val parsed = withContext(Dispatchers.IO) { parseCsv(csvDoc) }
            if (parsed.isEmpty()) {
                _uiState.value = UiState.Error("aliasmapping.csv is leeg of ongeldig.")
                return@launch
            }

            // 3) Cache opbouwen
            _uiState.value = UiState.Loading("Cache opbouwen…")
            withContext(Dispatchers.IO) { writeBinary(binFile, csvSize, csvMtime, parsed) }

            _uiState.value = UiState.Success(parsed)
        }
    }

    fun toggleSelection(tileName: String) {
        val set = LinkedHashSet(_selected.value)
        if (!set.add(tileName)) set.remove(tileName)
        _selected.value = set
    }

    fun isSelected(tileName: String): Boolean = _selected.value.contains(tileName)

    // ---------- CSV & BIN intern ----------

    private fun findCsvDocument(): DocumentFile? {
        val tree: Uri = setupManager.getPersistedTreeUri() ?: return null
        if (!setupManager.hasPersistedPermission(tree)) return null

        val root = DocumentFile.fromTreeUri(appContext, tree) ?: return null
        val appRoot = root.findFile("VoiceTally4")?.takeIf { it.isDirectory } ?: return null
        val assets = appRoot.findFile("assets")?.takeIf { it.isDirectory } ?: return null
        return assets.listFiles().firstOrNull { it.name?.equals("aliasmapping.csv", ignoreCase = true) == true }
    }

    private fun parseCsv(csvDoc: DocumentFile): List<SoortAlias> {
        val resolver = appContext.contentResolver
        val result = ArrayList<SoortAlias>(1024)

        resolver.openInputStream(csvDoc.uri).use { input ->
            if (input == null) return emptyList()
            input.bufferedReader(Charset.forName("UTF-8")).useLines { seq ->
                var firstNonEmptySeen = false
                seq.forEach { raw ->
                    val line = raw.trim()
                    if (line.isEmpty()) return@forEach

                    if (!firstNonEmptySeen) {
                        firstNonEmptySeen = true
                        val lower = line.lowercase()
                        // 1e niet-lege regel is header? Dan overslaan.
                        if (
                            lower.startsWith("soortid;") ||
                            "tilename" in lower ||
                            "canonical" in lower ||
                            lower.startsWith("#")
                        ) {
                            return@forEach
                        }
                    }

                    val cols = line.split(';')
                    if (cols.size >= 3) {
                        val soortId = cols[0].trim()
                        val canonical = cols[1].trim()
                        val tileName = cols[2].trim()
                        if (soortId.isEmpty() || canonical.isEmpty() || tileName.isEmpty()) return@forEach

                        val aliases =
                            if (cols.size > 3) cols.subList(3, minOf(cols.size, 24))
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            else emptyList()

                        result.add(SoortAlias(soortId, canonical, tileName, aliases))
                    }
                }
            }
        }
        return result
    }

    private fun readBinaryIfFresh(
        binFile: File,
        csvSize: Long,
        csvMtime: Long
    ): List<SoortAlias>? {
        if (!binFile.exists()) return null
        return try {
            DataInputStream(binFile.inputStream().buffered()).use { ins ->
                val version = ins.readInt()
                if (version != BIN_VERSION) return null
                val savedSize = ins.readLong()
                val savedMtime = ins.readLong()
                if (savedSize != csvSize || savedMtime != csvMtime) return null

                val count = ins.readInt()
                val out = ArrayList<SoortAlias>(count.coerceAtLeast(0))
                repeat(count) {
                    val soortId = ins.readUTF()
                    val canonical = ins.readUTF()
                    val tileName = ins.readUTF()
                    val aliasCount = ins.readInt()
                    val aliases = ArrayList<String>(aliasCount.coerceAtLeast(0))
                    repeat(aliasCount) { aliases.add(ins.readUTF()) }
                    out.add(SoortAlias(soortId, canonical, tileName, aliases))
                }
                out
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun writeBinary(
        binFile: File,
        csvSize: Long,
        csvMtime: Long,
        items: List<SoortAlias>
    ) {
        // Zorg dat de map bestaat
        binFile.parentFile?.mkdirs()
        runCatching {
            DataOutputStream(binFile.outputStream().buffered()).use { out ->
                out.writeInt(BIN_VERSION)
                out.writeLong(csvSize)
                out.writeLong(csvMtime)
                out.writeInt(items.size)
                for (it in items) {
                    out.writeUTF(it.soortId)
                    out.writeUTF(it.canonical)
                    out.writeUTF(it.tileName)
                    out.writeInt(it.aliases.size)
                    for (a in it.aliases) out.writeUTF(a)
                }
                out.flush()
            }
        }
    }

    companion object {
        private const val BIN_VERSION = 1
    }
}
