package com.yvesds.voicetally4.ui.data

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.yvesds.voicetally4.utils.io.DocumentsAccess
import com.yvesds.voicetally4.utils.io.StorageUtils
import com.yvesds.voicetally4.utils.text.TextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.zip.CRC32

/**
 * Single source of truth voor aliassen & display-namen (bin v2).
 *
 * Brontabel: /Documents/VoiceTally4/assets/aliasmapping.csv
 * - Eerst proberen via SAF (persisted tree URI).
 * - Fallback naar File-IO via StorageUtils (indien toegestaan).
 */
object UnifiedAliasStore {

    private const val TAG = "UnifiedAliasStore"
    private val UTF8: Charset = Charsets.UTF_8
    private const val MAGIC = "ALIASBIN"
    private const val VERSION_V2 = 2

    private const val BIN_DIR = "binaries"
    private const val BIN_NAME = "aliases.bin"
    private const val CSV_DIR = "assets"
    private const val CSV_NAME = "aliasmapping.csv"

    @Volatile private var initialized = false

    private var speciesCount: Int = 0
    private lateinit var speciesIdArr: Array<String>
    private lateinit var canonicalArr: Array<String>
    private lateinit var displayNameArr: Array<String>
    private lateinit var aliasStartArr: IntArray
    private lateinit var aliasCountArr: IntArray

    private var aliasCount: Int = 0
    private lateinit var aliasKeyArr: Array<String>
    private lateinit var aliasSpeciesRowIndexArr: IntArray

    // ----------------------- Public API -----------------------

    suspend fun ensureReady(context: Context) {
        if (initialized) return
        withContext(Dispatchers.IO) {
            if (initialized) return@withContext
            loadOrBuild(context)
            initialized = true
        }
    }

    fun getDisplayName(speciesId: String): String? {
        if (!initialized) return null
        val idx = speciesRowIndexById(speciesId) ?: return null
        return displayNameArr[idx]
    }

    fun getSpeciesIdForAlias(aliasRaw: String): String? {
        if (!initialized) return null
        val needle = TextNormalizer.normalizeBasic(aliasRaw)
        val idx = binarySearchAlias(needle)
        if (idx < 0) return null
        val speciesRow = aliasSpeciesRowIndexArr[idx]
        return speciesIdArr[speciesRow]
    }

    fun allTiles(): List<SpeciesTile> {
        if (!initialized) return emptyList()
        return List(speciesCount) { i ->
            SpeciesTile(
                speciesId = speciesIdArr[i],
                displayName = displayNameArr[i],
                canonical = canonicalArr[i]
            )
        }
    }

    // ----------------------- load/build -----------------------

    private fun loadOrBuild(context: Context) {
        try {
            val meta = resolveCsvMeta(context) ?: run {
                Log.e(TAG, "CSV ontbreekt: $CSV_DIR/$CSV_NAME (SAF en File-route faalden).")
                throw IllegalStateException("aliasmapping.csv niet gevonden.")
            }

            val binFile = resolveBinFile(context, ensureDir = true)

            if (binFile.exists()) {
                if (tryLoadFromBin(binFile, meta.size, meta.mtime, meta.crc)) {
                    Log.i(TAG, "UnifiedAliasStore: geladen uit bestaande bin v2.")
                    return
                } else {
                    Log.w(TAG, "Bestaande bin ongeldig/verouderd — wordt herbouwd.")
                }
            } else {
                Log.i(TAG, "Geen bestaande bin — wordt opgebouwd.")
            }

            buildFromCsv(meta, binFile)

        } catch (e: Exception) {
            Log.e(TAG, "Fout bij init: ${e.message}", e)
            resetEmpty()
        }
    }

    // Bron-metagegevens + open()-functie (SAF of File)
    private data class CsvMeta(
        val open: () -> InputStream,
        val size: Long,
        val mtime: Long,
        val crc: Int
    )

    /**
     * Probeer eerst SAF (persisted tree), daarna File-IO via StorageUtils.
     */
    private fun resolveCsvMeta(context: Context): CsvMeta? {
        // 1) SAF via jouw helper
        resolveCsvViaSaf(context)?.let { return it }

        // 2) File pad (fallback)
        val file = resolveCsvFile(context) ?: return null
        if (!file.exists() || !file.isFile) return null
        val size = file.length()
        val mtime = file.lastModified()
        val crc = calcCrc32(FileInputStream(file))
        return CsvMeta(
            open = { FileInputStream(file) },
            size = size,
            mtime = mtime,
            crc = crc
        )
    }

    /**
     * Vind exact "assets/aliasmapping.csv" via SAF met de bewaarde tree-uri.
     * Case-sensitief (alles is lowercase).
     */
    private fun resolveCsvViaSaf(context: Context): CsvMeta? {
        if (!DocumentsAccess.hasPersistedUri(context)) return null

        val doc: DocumentFile = DocumentsAccess
            .resolveRelativeFile(context, "$CSV_DIR/$CSV_NAME")
            ?: return null

        if (!doc.isFile || !doc.canRead()) return null

        val size = doc.length()
        val mtime = doc.lastModified()
        val crc = context.contentResolver.openInputStream(doc.uri)?.use { calcCrc32(it) } ?: return null

        return CsvMeta(
            open = { requireNotNull(context.contentResolver.openInputStream(doc.uri)) },
            size = size,
            mtime = mtime,
            crc = crc
        )
    }

    private fun tryLoadFromBin(
        binFile: File,
        expectedSize: Long,
        expectedMtime: Long,
        expectedCrc: Int
    ): Boolean = try {
        FileInputStream(binFile).channel.use { ch ->
            val bb = ch.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, ch.size())
            bb.order(ByteOrder.BIG_ENDIAN)

            val magic = readFixedAscii(bb, 8)
            if (magic != MAGIC) return false
            val version = bb.int
            if (version != VERSION_V2) return false
            val csvSize = bb.long
            val csvMtime = bb.long
            val csvCrc = bb.int
            if (csvSize != expectedSize || csvMtime != expectedMtime || csvCrc != expectedCrc) return false

            speciesCount = bb.int
            aliasCount = bb.int
            val sectionCount = bb.int
            if (sectionCount != 2) return false

            val sect0Offset = bb.long
            val sect0Length = bb.long
            val sect1Offset = bb.long
            val sect1Length = bb.long

            bb.position(sect0Offset.toInt())
            speciesIdArr = Array(speciesCount) { readString(bb) }
            canonicalArr = Array(speciesCount) { readString(bb) }
            displayNameArr = Array(speciesCount) { readString(bb) }
            aliasStartArr = IntArray(speciesCount) { bb.int }
            aliasCountArr = IntArray(speciesCount) { bb.int }

            bb.position(sect1Offset.toInt())
            aliasKeyArr = Array(aliasCount) { readString(bb) }
            aliasSpeciesRowIndexArr = IntArray(aliasCount) { bb.int }

            true
        }
    } catch (e: Exception) {
        Log.e(TAG, "tryLoadFromBin() faalde: ${e.message}")
        false
    }

    private fun buildFromCsv(
        meta: CsvMeta,
        binFile: File
    ) {
        val rows = parseCsv(meta.open)
        if (rows.isEmpty()) throw IllegalStateException("CSV heeft geen geldige rijen.")

        val spId = ArrayList<String>(rows.size)
        val spCanonical = ArrayList<String>(rows.size)
        val spDisplay = ArrayList<String>(rows.size)
        val aliasStart = IntArray(rows.size)
        val aliasCountPer = IntArray(rows.size)

        val aliasPairs = ArrayList<Pair<String, Int>>(rows.size * 4)

        rows.forEachIndexed { idx, r ->
            spId.add(r.speciesId)
            spCanonical.add(r.canonical)
            spDisplay.add(r.displayName)

            aliasStart[idx] = aliasPairs.size
            var cnt = 0
            r.aliases.forEach { raw ->
                val norm = TextNormalizer.normalizeBasic(raw)
                if (norm.isNotBlank()) {
                    aliasPairs.add(Pair(norm, idx)) // expliciet Pair() i.p.v. `to`
                    cnt++
                }
            }
            aliasCountPer[idx] = cnt
        }

        aliasPairs.sortWith(compareBy({ it.first }, { it.second }))

        speciesCount = rows.size
        aliasCount = aliasPairs.size

        speciesIdArr = spId.toTypedArray()
        canonicalArr = spCanonical.toTypedArray()
        displayNameArr = spDisplay.toTypedArray()
        aliasStartArr = aliasStart
        aliasCountArr = aliasCountPer

        aliasKeyArr = Array(aliasCount) { i -> aliasPairs[i].first }
        aliasSpeciesRowIndexArr = IntArray(aliasCount) { i -> aliasPairs[i].second }

        writeBinV2(binFile, meta.size, meta.mtime, meta.crc)
        Log.i(TAG, "aliases.bin v2 opgebouwd: species=$speciesCount, aliases=$aliasCount")
    }

    // ----------------------- CSV parsing -----------------------

    private data class CsvRow(
        val speciesId: String,
        val canonical: String,
        val displayName: String,
        val aliases: List<String>
    )

    private fun parseCsv(open: () -> InputStream): List<CsvRow> {
        val list = ArrayList<CsvRow>(1024)
        BufferedInputStream(open()).bufferedReader(UTF8).use { br ->
            var sep: Char? = null
            br.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                if (sep == null) sep = detectSeparator(line)
                val parts = line.split(sep ?: ';')
                if (parts.size < 3) return@forEach
                val speciesId = parts[0].trim()
                val canonical = parts[1].trim()
                val display = parts[2].trim()
                val aliases = if (parts.size > 3) {
                    parts.subList(3, parts.size).map { it.trim() }.filter { it.isNotEmpty() }
                } else emptyList()

                if (speciesId.isEmpty() || display.isEmpty()) return@forEach
                list += CsvRow(speciesId, canonical, display, aliases)
            }
        }
        return list
    }

    private fun detectSeparator(sample: String): Char =
        when {
            sample.contains(';') -> ';'
            sample.contains(',') -> ','
            else -> ';'
        }

    // ----------------------- Paths / IO helpers -----------------------

    /** File-route (fallback) naar .../VoiceTally4/assets/aliasmapping.csv */
    private fun resolveCsvFile(context: Context): File? {
        val dir = StorageUtils.getPublicAppDir(context, CSV_DIR) ?: return null
        return File(dir, CSV_NAME)
    }

    /** Bin staat in .../VoiceTally4/binaries/aliases.bin (zoals eerdere code). */
    private fun resolveBinFile(context: Context, ensureDir: Boolean): File {
        val dir = StorageUtils.getPublicAppDir(context, BIN_DIR)
            ?: throw IllegalStateException("Kan map $BIN_DIR niet openen")
        if (ensureDir && !dir.exists()) dir.mkdirs()
        return File(dir, BIN_NAME)
    }

    private fun calcCrc32(input: InputStream): Int {
        val crc = CRC32()
        BufferedInputStream(input).use { inp ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val r = inp.read(buf)
                if (r <= 0) break
                crc.update(buf, 0, r)
            }
        }
        return crc.value.toInt()
    }

    // ----------------------- Binary helpers -----------------------

    private fun writeBinV2(
        binFile: File,
        csvSize: Long,
        csvMtime: Long,
        csvCrc: Int
    ) {
        val tmp = File(binFile.parentFile, "$BIN_NAME.tmp")
        FileOutputStream(tmp).use { fos ->
            BufferedOutputStream(fos).use { bos ->
                // Bouw secties met exacte grootte
                val sectSpecies = writeSpeciesSectionToBuffer()
                val sectAliases = writeAliasesSectionToBuffer()

                // === Header ===
                // Velden: magic(8) + version(4) + csvSize(8) + csvMtime(8) + csvCrc(4)
                //       + speciesCount(4) + aliasCount(4) + sectionCount(4)
                //       + (offset(8)+length(8)) * 2
                val headerSize = 8 + 4 + 8 + 8 + 4 + 4 + 4 + 4 + (8 + 8) * 2
                val header = ByteBuffer.allocate(headerSize).order(ByteOrder.BIG_ENDIAN)

                writeFixedAscii(header, MAGIC, 8)
                header.putInt(VERSION_V2)
                header.putLong(csvSize)
                header.putLong(csvMtime)
                header.putInt(csvCrc)
                header.putInt(speciesCount)
                header.putInt(aliasCount)
                header.putInt(2) // sectionCount

                var offset = header.capacity().toLong()
                val sect0Offset = offset
                val sect0Len = sectSpecies.limit().toLong()
                offset += sect0Len
                val sect1Offset = offset
                val sect1Len = sectAliases.limit().toLong()
                offset += sect1Len

                header.putLong(sect0Offset)
                header.putLong(sect0Len)
                header.putLong(sect1Offset)
                header.putLong(sect1Len)

                header.flip()
                bos.write(header.array(), 0, header.limit())
                bos.write(sectSpecies.array(), 0, sectSpecies.limit())
                bos.write(sectAliases.array(), 0, sectAliases.limit())
                bos.flush()
            }
        }
        if (binFile.exists()) binFile.delete()
        tmp.renameTo(binFile)
    }

    private fun writeSpeciesSectionToBuffer(): ByteBuffer {
        // Bereken exacte bytes:
        // per string: 4 (length) + bytes.size, plus 2 ints (aliasStart, aliasCount)
        var total = 0
        for (i in 0 until speciesCount) {
            total += 4 + speciesIdArr[i].toByteArray(UTF8).size
            total += 4 + canonicalArr[i].toByteArray(UTF8).size
            total += 4 + displayNameArr[i].toByteArray(UTF8).size
            total += 4 /*aliasStart*/ + 4 /*aliasCount*/
        }
        val bb = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN)
        repeat(speciesCount) { i ->
            writeString(bb, speciesIdArr[i])
            writeString(bb, canonicalArr[i])
            writeString(bb, displayNameArr[i])
            bb.putInt(aliasStartArr[i])
            bb.putInt(aliasCountArr[i])
        }
        bb.flip()
        return bb
    }

    private fun writeAliasesSectionToBuffer(): ByteBuffer {
        // per alias: 4 + bytes(alias) + 4 (speciesRowIndex)
        var total = 0
        for (i in 0 until aliasCount) {
            total += 4 + aliasKeyArr[i].toByteArray(UTF8).size
            total += 4
        }
        val bb = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN)
        repeat(aliasCount) { i ->
            writeString(bb, aliasKeyArr[i])
            bb.putInt(aliasSpeciesRowIndexArr[i])
        }
        bb.flip()
        return bb
    }

    private fun readFixedAscii(bb: ByteBuffer, len: Int): String {
        val arr = ByteArray(len)
        bb.get(arr)
        return arr.toString(Charsets.US_ASCII)
    }

    private fun writeFixedAscii(bb: ByteBuffer, s: String, len: Int) {
        val arr = ByteArray(len) { 0 }
        val bytes = s.toByteArray(Charsets.US_ASCII)
        val n = minOf(len, bytes.size)
        System.arraycopy(bytes, 0, arr, 0, n)
        bb.put(arr)
    }

    private fun readString(bb: ByteBuffer): String {
        val n = bb.int
        if (n < 0 || n > (64 * 1024 * 1024)) throw IllegalStateException("String length invalid: $n")
        val bytes = ByteArray(n)
        bb.get(bytes)
        return String(bytes, UTF8)
    }

    private fun writeString(bb: ByteBuffer, s: String) {
        val bytes = s.toByteArray(UTF8)
        bb.putInt(bytes.size)
        bb.put(bytes)
    }

    // ----------------------- Lookups -----------------------

    private fun speciesRowIndexById(speciesId: String): Int? {
        for (i in 0 until speciesCount) {
            if (speciesIdArr[i] == speciesId) return i
        }
        return null
    }

    private fun binarySearchAlias(needle: String): Int {
        var lo = 0
        var hi = aliasCount - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val cmp = aliasKeyArr[mid].compareTo(needle)
            when {
                cmp < 0 -> lo = mid + 1
                cmp > 0 -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    private fun resetEmpty() {
        speciesCount = 0
        aliasCount = 0
        speciesIdArr = emptyArray()
        canonicalArr = emptyArray()
        displayNameArr = emptyArray()
        aliasStartArr = IntArray(0)
        aliasCountArr = IntArray(0)
        aliasKeyArr = emptyArray()
        aliasSpeciesRowIndexArr = IntArray(0)
        initialized = true
    }
}

data class SpeciesTile(
    val speciesId: String,
    val displayName: String,
    val canonical: String
)
