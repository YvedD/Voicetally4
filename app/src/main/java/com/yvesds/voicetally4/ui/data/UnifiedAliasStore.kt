package com.yvesds.voicetally4.ui.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.yvesds.voicetally4.utils.io.DocumentsAccess
import com.yvesds.voicetally4.utils.io.StorageUtils
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/**
 * Unified alias store (één bestand, twee secties):
 *  - Sectie 1 (SelectionList v2): volledige records (id, canonical, display, aliases)
 *  - Sectie 2 (SpeechIndex v1): alias -> (id, displayName)
 * Binair: Documents/VoiceTally4/binaries/aliases.vt4bin
 *
 * Zoekstrategie CSV (Android 13/14 best practice):
 *  1) **SAF**: als user een tree heeft gekoppeld, lees "assets/aliasmapping.csv" via DocumentFile
 *  2) App-private File (StorageUtils fallback)
 *  3) Publiek File (kanRead kan false zijn, we proberen het niet direct te gebruiken)
 *  4) MediaStore brede zoekopdracht
 */
object UnifiedAliasStore {

    private const val TAG = "UnifiedAliasStore"

    private const val MAGIC = "VT4ALX\u0000" // 8 bytes
    private const val FILE_VERSION = 1
    private const val SECTION_COUNT = 2

    private const val SEC_SELECTION = 1
    private const val SEC_SPEECH = 2

    private const val SEL_VERSION = 2
    private const val SPEECH_VERSION = 1

    private const val LEGACY_SELECTION_MAGIC = "ALIASBIN"
    private const val LEGACY_SPEECH_MAGIC = "VT4ALIAS1"

    private const val CSV_NAME = "aliasmapping.csv"
    private const val REL_BASE = "Documents/VoiceTally4"
    private const val REL_ASSETS = "$REL_BASE/assets"

    data class SelectionEntry(
        val soortId: String,
        val canonical: String,
        val display: String,
        val aliases: List<String>
    )
    data class SpeechEntry(val id: String, val displayName: String)

    private sealed class CsvSource {
        data class SafSource(val doc: DocumentFile, val size: Long, val mtimeSecs: Long) : CsvSource()
        data class FileSource(val file: File) : CsvSource()
        data class MediaSource(val uri: Uri, val size: Long, val mtimeSecs: Long, val relPath: String) : CsvSource()
    }

    fun preload(context: Context) {
        try {
            val unified = readUnifiedIfFresh(context)
            if (unified == null) {
                readLegacySelectionMap(context)
                readLegacySpeech(context)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "preload: ${t.message}")
        }
    }

    fun loadSelectionList(context: Context): List<SelectionEntry> {
        readUnifiedIfFresh(context)?.let { (sel, _) -> if (sel.isNotEmpty()) return sel }

        readLegacySelectionMap(context)?.let { legacy ->
            if (legacy.isNotEmpty()) {
                Log.d(TAG, "Using legacy selection map (aliases.bin)")
                return legacy.map { (canonical, display) ->
                    SelectionEntry(soortId = canonical, canonical = canonical, display = display, aliases = emptyList())
                }
            }
        }

        val csvSrc = findCsvSource(context)
        if (csvSrc == null) {
            Log.e(TAG, "CSV source not found (SAF/File/MediaStore).")
            return emptyList()
        }
        val (csvSize, csvMtime) = csvMeta(csvSrc)

        val parsed = when (csvSrc) {
            is CsvSource.SafSource -> {
                Log.d(TAG, "Parsing CSV via SAF: ${csvSrc.doc.uri}")
                context.contentResolver.openInputStream(csvSrc.doc.uri)?.use { CsvCompat.parseFullStream(it) } ?: emptyList()
            }
            is CsvSource.FileSource -> {
                Log.d(TAG, "Parsing CSV from File: ${csvSrc.file.absolutePath}")
                CsvCompat.parseFullFile(csvSrc.file)
            }
            is CsvSource.MediaSource -> {
                Log.d(TAG, "Parsing CSV from MediaStore: ${csvSrc.relPath} (size=$csvSize, mtime=$csvMtime)")
                context.contentResolver.openInputStream(csvSrc.uri)?.use { CsvCompat.parseFullStream(it) } ?: emptyList()
            }
        }
        if (parsed.isEmpty()) {
            Log.e(TAG, "CSV parsed empty; check delimiter/format.")
            return emptyList()
        }

        val speech = readLegacySpeech(context) ?: emptyMap()
        writeUnified(context, csvSize, csvMtime, parsed, speech)
        return parsed
    }

    fun loadSpeechIndex(context: Context): Map<String, SpeechEntry> {
        readUnifiedIfFresh(context)?.let { (_, speech) -> if (speech.isNotEmpty()) return speech }
        return readLegacySpeech(context) ?: emptyMap()
    }

    // ---------------- Unified read/write ----------------

    private fun readUnifiedIfFresh(context: Context): Pair<List<SelectionEntry>, Map<String, SpeechEntry>>? {
        val csvSrc = findCsvSource(context) ?: return null
        val (csvSize, csvMtime) = csvMeta(csvSrc)

        val file = getUnifiedFile(context)
        if (!file.exists() || !file.canRead()) return null

        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { din ->
                val headerMagic = ByteArray(8)
                din.readFully(headerMagic)
                if (headerMagic.decodeToString() != MAGIC) return null

                val fileVersion = din.readUnsignedShort()
                if (fileVersion != FILE_VERSION) return null

                val sectionCount = din.readUnsignedShort()
                if (sectionCount != SECTION_COUNT) return null

                val cachedSize = din.readLong()
                val cachedMtime = din.readLong()
                if (cachedSize != csvSize || cachedMtime != csvMtime) {
                    Log.d(TAG, "Unified not fresh (have size=$cachedSize,mtime=$cachedMtime; need size=$csvSize,mtime=$csvMtime)")
                    return null
                }

                data class Sec(val id: Int, val version: Int, val offset: Long, val length: Long)
                val secs = ArrayList<Sec>(sectionCount)
                repeat(sectionCount) {
                    secs += Sec(din.readUnsignedShort(), din.readUnsignedShort(), din.readLong(), din.readLong())
                }

                var selection: List<SelectionEntry> = emptyList()
                var speech: Map<String, SpeechEntry> = emptyMap()

                RandomAccessFile(file, "r").use { raf ->
                    for (s in secs) {
                        raf.seek(s.offset)
                        when (s.id) {
                            SEC_SELECTION -> selection = if (s.version == SEL_VERSION) readSelectionSectionV2(raf) else readSelectionSectionV1(raf)
                            SEC_SPEECH -> if (s.version == SPEECH_VERSION) speech = readSpeechSectionV1(raf)
                        }
                    }
                }
                selection to speech
            }
        } catch (t: Throwable) {
            Log.w(TAG, "readUnifiedIfFresh failed: ${t.message}")
            return null
        }
    }

    private fun readSelectionSectionV2(raf: RandomAccessFile): List<SelectionEntry> {
        val out = ArrayList<SelectionEntry>()
        val count = raf.readIntBE()
        repeat(count) {
            val soortId = raf.readUtf8LenPrefixed()
            val canonical = raf.readUtf8LenPrefixed()
            val display = raf.readUtf8LenPrefixed()
            val aliasCount = raf.readIntBE()
            val aliases = ArrayList<String>(aliasCount)
            repeat(aliasCount) { aliases.add(raf.readUtf8LenPrefixed()) }
            if (canonical.isNotEmpty() && display.isNotEmpty()) {
                out.add(SelectionEntry(soortId, canonical, display, aliases))
            }
        }
        return out
    }

    private fun readSelectionSectionV1(raf: RandomAccessFile): List<SelectionEntry> {
        val out = ArrayList<SelectionEntry>()
        val count = raf.readIntBE()
        repeat(count) {
            val canonical = raf.readUtf8LenPrefixed()
            val display = raf.readUtf8LenPrefixed()
            if (canonical.isNotEmpty() && display.isNotEmpty()) {
                out.add(SelectionEntry(soortId = canonical, canonical = canonical, display = display, aliases = emptyList()))
            }
        }
        return out
    }

    private fun readSpeechSectionV1(raf: RandomAccessFile): Map<String, SpeechEntry> {
        val out = LinkedHashMap<String, SpeechEntry>()
        val count = raf.readIntBE()
        repeat(count) {
            val alias = raf.readUtf8LenPrefixed()
            val id = raf.readUtf8LenPrefixed()
            val display = raf.readUtf8LenPrefixed()
            if (alias.isNotEmpty() && id.isNotEmpty()) {
                out[alias] = SpeechEntry(id, display)
            }
        }
        return out
    }

    private fun writeUnified(
        context: Context,
        csvSize: Long,
        csvMtime: Long,
        selection: List<SelectionEntry>,
        speech: Map<String, SpeechEntry>
    ) {
        val file = getUnifiedFile(context)
        file.parentFile?.mkdirs()

        val selBaos = ByteArrayOutputStream()
        DataOutputStream(BufferedOutputStream(selBaos)).use { dout ->
            dout.writeInt(selection.size)
            for (e in selection) {
                writeUtf8LenPrefixed(dout, e.soortId)
                writeUtf8LenPrefixed(dout, e.canonical)
                writeUtf8LenPrefixed(dout, e.display)
                dout.writeInt(e.aliases.size)
                for (a in e.aliases) writeUtf8LenPrefixed(dout, a)
            }
            dout.flush()
        }
        val selBytes = selBaos.toByteArray()

        val spBaos = ByteArrayOutputStream()
        DataOutputStream(BufferedOutputStream(spBaos)).use { dout ->
            dout.writeInt(speech.size)
            for ((alias, entry) in speech) {
                writeUtf8LenPrefixed(dout, alias)
                writeUtf8LenPrefixed(dout, entry.id)
                writeUtf8LenPrefixed(dout, entry.displayName)
            }
            dout.flush()
        }
        val spBytes = spBaos.toByteArray()

        DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { dout ->
            dout.write(MAGIC.toByteArray(Charsets.US_ASCII))
            dout.writeShort(FILE_VERSION)
            dout.writeShort(SECTION_COUNT)
            dout.writeLong(csvSize)
            dout.writeLong(csvMtime)

            val headerSize = 8 + 2 + 2 + 8 + 8
            val tableSize = SECTION_COUNT * (2 + 2 + 8 + 8)
            var offset = (headerSize + tableSize).toLong()

            dout.writeShort(SEC_SELECTION)
            dout.writeShort(SEL_VERSION)
            dout.writeLong(offset)
            dout.writeLong(selBytes.size.toLong())
            offset += selBytes.size

            dout.writeShort(SEC_SPEECH)
            dout.writeShort(SPEECH_VERSION)
            dout.writeLong(offset)
            dout.writeLong(spBytes.size.toLong())

            dout.write(selBytes)
            dout.write(spBytes)
            dout.flush()
        }
    }

    // ---------------- CSV-locator ----------------

    private fun findCsvSource(context: Context): CsvSource? {
        // (1) SAF (persistente toegang)
        if (DocumentsAccess.hasPersistedUri(context)) {
            val doc = DocumentsAccess.resolveRelativeFile(context, "assets/$CSV_NAME")
            if (doc != null && doc.isFile) {
                val size = doc.length()
                val mtime = doc.lastModified() / 1000L
                Log.d(TAG, "CSV via SAF: ${doc.uri} (size=$size,mtime=$mtime)")
                return CsvSource.SafSource(doc, size, mtime)
            } else {
                // map "assets" bestaat? (optioneel)
                DocumentsAccess.getOrCreateSubdir(context, "assets")
            }
        }

        // (2) App-private (via StorageUtils fallback)
        val appDir = StorageUtils.getPublicAppDir(context, "assets")
        val appFile = File(appDir, CSV_NAME)
        if (appFile.exists() && appFile.isFile && appFile.canRead()) {
            Log.d(TAG, "CSV via app-private File: ${appFile.absolutePath}")
            return CsvSource.FileSource(appFile)
        }

        // (3) Publiek bestand: niet op vertrouwen (canRead kan false zijn), maar we loggen voor debug
        val publicFile = publicCsvFile()
        if (publicFile.exists() && publicFile.isFile) {
            Log.d(TAG, "CSV publiek path gedetecteerd: ${publicFile.absolutePath} (canRead=${publicFile.canRead()})")
        }

        // (4) MediaStore brede zoektocht
        val msPrimary = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val sizeCol = MediaStore.Files.FileColumns.SIZE
        val dateModCol = MediaStore.Files.FileColumns.DATE_MODIFIED
        val relCol = MediaStore.Files.FileColumns.RELATIVE_PATH
        val nameCol = MediaStore.Files.FileColumns.DISPLAY_NAME

        fun pickNewest(list: MutableList<CsvSource.MediaSource>): CsvSource.MediaSource? {
            if (list.isEmpty()) return null
            list.sortByDescending { it.mtimeSecs }
            list.firstOrNull { it.relPath.contains("VoiceTally4", ignoreCase = true) }?.let { return it }
            return list.first()
        }

        fun query(selection: String, args: Array<String>): CsvSource.MediaSource? {
            val out = mutableListOf<CsvSource.MediaSource>()
            context.contentResolver.query(
                msPrimary,
                arrayOf(MediaStore.Files.FileColumns._ID, sizeCol, dateModCol, relCol, nameCol),
                selection, args, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val size = c.getLong(1)
                    val mtime = c.getLong(2)
                    val rel = c.getString(3) ?: ""
                    val name = c.getString(4) ?: ""
                    if (name.equals(CSV_NAME, ignoreCase = true)) {
                        val uri = Uri.withAppendedPath(msPrimary, id.toString())
                        out += CsvSource.MediaSource(uri, size, mtime, rel)
                    }
                }
            }
            return pickNewest(out)
        }

        query("$nameCol=? AND $relCol LIKE ?", arrayOf(CSV_NAME, "$REL_ASSETS/%"))?.let {
            Log.d(TAG, "CSV via MediaStore (assets): rel=${it.relPath}, mtime=${it.mtimeSecs}")
            return it
        }
        query("$nameCol=? AND $relCol LIKE ?", arrayOf(CSV_NAME, "$REL_BASE/%"))?.let {
            Log.d(TAG, "CSV via MediaStore (VoiceTally4/*): rel=${it.relPath}, mtime=${it.mtimeSecs}")
            return it
        }
        query("$nameCol=?", arrayOf(CSV_NAME))?.let {
            Log.d(TAG, "CSV via MediaStore (global by name): rel=${it.relPath}, mtime=${it.mtimeSecs}")
            return it
        }

        Log.w(TAG, "CSV niet gevonden via SAF/File/MediaStore.")
        return null
    }

    private fun csvMeta(src: CsvSource): Pair<Long, Long> = when (src) {
        is CsvSource.SafSource -> src.size to src.mtimeSecs
        is CsvSource.FileSource -> src.file.length() to (src.file.lastModified() / 1000L)
        is CsvSource.MediaSource -> src.size to src.mtimeSecs
    }

    private fun publicCsvFile(): File {
        val publicDocs = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            @Suppress("DEPRECATION")
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        } else {
            @Suppress("DEPRECATION")
            File(Environment.getExternalStorageDirectory(), "Documents")
        }
        return File(File(publicDocs, "VoiceTally4/assets"), CSV_NAME)
    }

    // ---------------- Legacy readers ----------------

    private fun readLegacySelectionMap(context: Context): Map<String, String>? {
        val binDir = StorageUtils.getPublicAppDir(context, "binaries")
        val file = File(binDir, "aliases.bin")
        if (!file.exists() || !file.canRead()) return null
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { din ->
                val magic = ByteArray(8)
                din.readFully(magic)
                if (magic.decodeToString() != LEGACY_SELECTION_MAGIC) return@use null
                val version = din.readInt()
                if (version != 1) return@use null
                din.readLong()
                din.readLong()
                val count = din.readInt()
                val out = LinkedHashMap<String, String>(count)
                repeat(count) {
                    val canonical = din.readUtf8LenPrefixed()
                    val display = din.readUtf8LenPrefixed()
                    if (canonical.isNotEmpty() && display.isNotEmpty()) out[canonical] = display
                }
                out
            }
        } catch (t: Throwable) {
            Log.w(TAG, "readLegacySelectionMap: ${t.message}")
            null
        }
    }

    private fun readLegacySpeech(context: Context): Map<String, SpeechEntry>? {
        val binDir = StorageUtils.getPublicAppDir(context, "binaries")
        val file = File(binDir, "aliasmapping.bin")
        if (!file.exists() || !file.canRead()) return null
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { din ->
                val headerBytes = ByteArray(LEGACY_SPEECH_MAGIC.length)
                din.readFully(headerBytes)
                val header = headerBytes.decodeToString()
                if (header != LEGACY_SPEECH_MAGIC) return@use null

                val out = LinkedHashMap<String, SpeechEntry>()
                while (true) {
                    val alias = try { din.readUTF() } catch (_: EOFException) { break }
                    val id = din.readUTF()
                    val display = din.readUTF()
                    if (alias.isNotEmpty() && id.isNotEmpty()) {
                        out[alias.trim().lowercase()] = SpeechEntry(id, display)
                    }
                }
                out
            }
        } catch (t: Throwable) {
            Log.w(TAG, "readLegacySpeech: ${t.message}")
            null
        }
    }

    // ---------------- IO helpers ----------------

    private fun RandomAccessFile.readIntBE(): Int {
        val b = ByteArray(4)
        readFully(b)
        return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).int
    }

    private fun RandomAccessFile.readUtf8LenPrefixed(): String {
        val len = readIntBE()
        if (len <= 0 || len > 4_000_000) return ""
        val buf = ByteArray(len)
        readFully(buf)
        return buf.toString(Charset.forName("UTF-8"))
    }

    private fun DataInputStream.readUtf8LenPrefixed(): String {
        val len = readInt()
        if (len <= 0 || len > 4_000_000) return ""
        val buf = ByteArray(len)
        readFully(buf)
        return buf.toString(Charset.forName("UTF-8"))
    }

    private fun writeUtf8LenPrefixed(dout: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        dout.writeInt(bytes.size)
        dout.write(bytes)
    }

    private fun getUnifiedFile(context: Context): File {
        val dir = StorageUtils.getPublicAppDir(context, "binaries")
        return File(dir, "aliases.vt4bin")
    }
}

/** CSV-parser die volledige SelectionEntry’s oplevert. */
private object CsvCompat {

    fun parseFullFile(csvFile: File): List<UnifiedAliasStore.SelectionEntry> {
        csvFile.inputStream().use { return parseFullStream(it) }
    }

    fun parseFullStream(input: InputStream): List<UnifiedAliasStore.SelectionEntry> {
        val rows = ArrayList<List<String>>()
        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                rows.add(line.split(Regex("[;,]")).map { it.trim() })
            }
        }
        if (rows.isEmpty()) return emptyList()

        val out = ArrayList<UnifiedAliasStore.SelectionEntry>(rows.size)
        for (cols in rows) {
            if (cols.size < 2) continue

            val canonical = cols[0]
            val display = cols[1]
            var soortId: String? = null
            val aliases = ArrayList<String>()

            if (cols.size > 2) {
                for (i in 2 until cols.size) {
                    val v = cols[i]
                    if (v.isEmpty()) continue
                    if (soortId == null && v.all { it.isDigit() }) {
                        soortId = v
                    } else {
                        aliases.add(v)
                    }
                }
            }
            val idFinal = (soortId ?: canonical)
            if (canonical.isNotEmpty() && display.isNotEmpty()) {
                out.add(UnifiedAliasStore.SelectionEntry(idFinal, canonical, display, aliases))
            }
        }
        val dedup = LinkedHashMap<String, UnifiedAliasStore.SelectionEntry>(out.size)
        for (e in out) dedup[e.canonical] = e
        return dedup.values.toList()
    }
}
