package com.yvesds.voicetally4.ui.domein

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yvesds.voicetally4.ui.core.SetupManager
import com.yvesds.voicetally4.ui.data.SoortAlias
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import javax.inject.Inject

/**
 * Laadt de soort-aliases lazily en volledig off-main.
 * - Probeert eerst een binaire cache: Documents/VoiceTally4/binaries/aliasmapping.bin
 * - Als miss/ongeldig: parse CSV uit de SAF-tree: VoiceTally4/assets/aliasmapping.csv, en bouw de cache.
 */
@HiltViewModel
class SoortSelectieViewModel @Inject constructor() : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Data(val items: List<SoortAlias>) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun loadAliases(context: Context, setupManager: SetupManager) {
        // Herstartbaar; idempotent genoeg omdat we state updaten
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = UiState.Loading
            val res = runCatching {
                loadFromBinaryOrCsv(context, setupManager)
            }.getOrElse { ex ->
                _state.value = UiState.Error(ex.message ?: "Onbekende fout bij inlezen.")
                return@launch
            }

            if (res == null) {
                _state.value = UiState.Error("aliasmapping.csv niet gevonden of geen permissie.")
            } else {
                _state.value = UiState.Data(res)
            }
        }
    }

    // -- BIN bestandskenmerken --
    private val MAGIC = 0x41_4C_49_41 // 'ALIA' (Alias)
    private val VERSION = 1

    private fun loadFromBinaryOrCsv(
        context: Context,
        setupManager: SetupManager
    ): List<SoortAlias>? {
        val treeUri: Uri = setupManager.getPersistedTreeUri() ?: return null
        if (!setupManager.hasPersistedPermission(treeUri)) return null

        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val appRoot = tree.findFile("VoiceTally4")?.takeIf { it.isDirectory } ?: return null

        // 1) Zoek CSV
        val assetsDir = appRoot.findFile("assets")?.takeIf { it.isDirectory } ?: return null
        val csvFile = assetsDir.listFiles().firstOrNull {
            it.name?.equals("aliasmapping.csv", ignoreCase = true) == true
        } ?: return null

        val csvLen = csvFile.length()
        val csvMod = csvFile.lastModified()

        // 2) Zoek binaries/aliasmapping.bin
        val bins = appRoot.findFile("binaries")?.takeIf { it.isDirectory }
            ?: appRoot.createDirectory("binaries")
            ?: return loadCsv(context, csvFile).also { /* geen cache mogelijk */ }

        var bin = bins.findFile("aliasmapping.bin")
        // 3) Probeer lezen
        if (bin != null && bin.isFile) {
            try {
                context.contentResolver.openInputStream(bin.uri)?.use { base ->
                    DataInputStream(BufferedInputStream(base)).use { din ->
                        val magic = din.readInt()
                        val version = din.readInt()
                        val len = din.readLong()
                        val mod = din.readLong()
                        if (magic == MAGIC && version == VERSION && len == csvLen && mod == csvMod) {
                            val count = din.readInt().coerceAtLeast(0)
                            val out = ArrayList<SoortAlias>(count.coerceAtLeast(256))
                            repeat(count) {
                                val soortId = readUtf8(din)
                                val canonical = readUtf8(din)
                                val tileName = readUtf8(din)
                                val aliasCount = din.readInt().coerceAtLeast(0)
                                val aliases = ArrayList<String>(aliasCount)
                                repeat(aliasCount) { aliases.add(readUtf8(din)) }
                                out.add(SoortAlias(soortId, canonical, tileName, aliases))
                            }
                            return out
                        }
                    }
                }
            } catch (_: Throwable) {
                // valt terug op CSV
            }
        }

        // 4) CSV parse + cache schrijven
        val parsed = loadCsv(context, csvFile)
        // bouw/overschrijf bin
        if (parsed.isNotEmpty()) {
            try {
                if (bin == null || !bin.isFile) {
                    bin = bins.createFile("application/octet-stream", "aliasmapping.bin")
                }
                bin?.uri?.let { uri ->
                    context.contentResolver.openOutputStream(uri, "rwt")?.use { base ->
                        DataOutputStream(BufferedOutputStream(base)).use { dout ->
                            dout.writeInt(MAGIC)
                            dout.writeInt(VERSION)
                            dout.writeLong(csvLen)
                            dout.writeLong(csvMod)
                            dout.writeInt(parsed.size)
                            parsed.forEach { rec ->
                                writeUtf8(dout, rec.soortId)
                                writeUtf8(dout, rec.canonical)
                                writeUtf8(dout, rec.tileName)
                                dout.writeInt(rec.aliases.size)
                                rec.aliases.forEach { writeUtf8(dout, it) }
                            }
                            dout.flush()
                        }
                    }
                }
            } catch (_: Throwable) {
                // best-effort; cache mag falen zonder UI-impact
            }
        }
        return parsed
    }

    private fun loadCsv(context: Context, csv: DocumentFile): List<SoortAlias> {
        val resolver = context.contentResolver
        resolver.openInputStream(csv.uri)?.use { inStream ->
            InputStreamReader(inStream, StandardCharsets.UTF_8).buffered().use { reader ->
                val out = ArrayList<SoortAlias>(1024)
                var firstNonEmptySeen = false
                reader.forEachLine { raw ->
                    val line = raw.trim()
                    if (line.isEmpty()) return@forEachLine

                    // Sla 1e niet-lege headerachtige lijn over
                    if (!firstNonEmptySeen) {
                        firstNonEmptySeen = true
                        val lower = line.lowercase()
                        if (
                            lower.startsWith("soortid;") ||
                            "tilename" in lower ||
                            "canonical" in lower ||
                            lower.startsWith("#")
                        ) {
                            return@forEachLine
                        }
                    }

                    val cols = line.split(';')
                    if (cols.size >= 3) {
                        val soortId = cols[0].trim()
                        val canonical = cols[1].trim()
                        val tileName = cols[2].trim()
                        if (soortId.isEmpty() || canonical.isEmpty() || tileName.isEmpty()) return@forEachLine
                        val aliases = if (cols.size > 3) {
                            cols.subList(3, minOf(cols.size, 24))
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                        } else emptyList()
                        out.add(SoortAlias(soortId, canonical, tileName, aliases))
                    }
                }
                return out
            }
        }
        return emptyList()
    }

    // --- Compacte UTF-8 helpers (length:int + bytes) ---
    private fun writeUtf8(dout: DataOutputStream, s: String) {
        val bytes = s.toByteArray(StandardCharsets.UTF_8)
        dout.writeInt(bytes.size)
        dout.write(bytes)
    }

    private fun readUtf8(din: DataInputStream): String {
        val len = din.readInt()
        if (len <= 0) return ""
        val buf = ByteArray(len)
        var read = 0
        while (read < len) {
            val r = din.read(buf, read, len - read)
            if (r <= 0) break
            read += r
        }
        return String(buf, 0, read, StandardCharsets.UTF_8)
    }
}
