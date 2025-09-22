package com.yvesds.voicetally4.data

import android.content.Context
import com.yvesds.voicetally4.utils.io.StorageUtils
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.charset.Charset

/**
 * Snelle loader voor de selectie-aliascache:
 *  - Probeert binaire cache (ALIASBIN) te lezen: canonical -> displayName (kolom 2).
 *  - Valt terug op CSV + schrijft binaire cache, net zoals in SoortSelectieViewModel.
 *
 * Dit is een pure util die je bij app-start kunt aanroepen (preload),
 * en/of vanuit je ViewModel (als bron van waarheid).
 */
object AliasDisplayNamesLoader {

    private const val MAGIC = "ALIASBIN" // exact 8 bytes incl. 0x00 terminator bij schrijven
    private const val VERSION = 1

    /** Laad canoniek->displayName map. Probeert eerst .bin, dan CSV en bouwt .bin opnieuw. */
    fun load(context: Context): Map<String, String> {
        val csv = findCsv(context) ?: return emptyMap()
        val csvSize = csv.length()
        val csvMtime = csv.lastModified()

        val binDir = StorageUtils.getPublicAppDir(context, "binaries")
        val binFile = File(binDir, "aliases.bin")

        readBinaryIfFresh(binFile, csvSize, csvMtime)?.let { return it }

        val parsed = parseCsv(csv)
        if (parsed.isEmpty()) return emptyMap()

        writeBinary(binFile, csvSize, csvMtime, parsed)
        return parsed
    }

    /** Alleen lezen van .bin; return null als onfris of ongeldig. */
    fun readBinaryIfFresh(binFile: File, csvSize: Long, csvMtime: Long): Map<String, String>? {
        if (!binFile.exists() || !binFile.canRead()) return null
        return try {
            DataInputStream(BufferedInputStream(binFile.inputStream())).use { din ->
                val magic = ByteArray(8)
                din.readFully(magic)
                val magicStr = magic.decodeToString()
                if (magicStr != MAGIC) return null
                val version = din.readInt()
                if (version != VERSION) return null
                val cachedSize = din.readLong()
                val cachedMtime = din.readLong()
                if (cachedSize != csvSize || cachedMtime != csvMtime) return null
                val count = din.readInt()
                val out = HashMap<String, String>(count)
                repeat(count) {
                    val canonical = readUtf8String(din)
                    val display = readUtf8String(din)
                    if (canonical.isNotEmpty() && display.isNotEmpty()) {
                        out[canonical] = display
                    }
                }
                out
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** CSV parser: kolom 1 = canonical, kolom 2 = displayName; ';/,' als separator, # = comment. */
    fun parseCsv(csvFile: File): Map<String, String> {
        val result = LinkedHashMap<String, String>(1024)
        csvFile.forEachLine(Charsets.UTF_8) { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachLine
            val cols = line.split(Regex("[;,]"))
            if (cols.size < 2) return@forEachLine
            val canonical = cols[0].trim()
            val display = cols[1].trim()
            if (canonical.isNotEmpty() && display.isNotEmpty()) {
                result[canonical] = display
            }
        }
        return result
    }

    fun writeBinary(
        binFile: File,
        csvSize: Long,
        csvMtime: Long,
        map: Map<String, String>
    ) {
        binFile.parentFile?.mkdirs()
        DataOutputStream(BufferedOutputStream(binFile.outputStream())).use { dout ->
            val magic = ByteArray(8) { 0 }
            val m = MAGIC.toByteArray(Charsets.US_ASCII)
            System.arraycopy(m, 0, magic, 0, m.size.coerceAtMost(8))
            dout.write(magic)
            dout.writeInt(VERSION)
            dout.writeLong(csvSize)
            dout.writeLong(csvMtime)
            dout.writeInt(map.size)
            for ((canonical, display) in map) {
                writeUtf8String(dout, canonical)
                writeUtf8String(dout, display)
            }
            dout.flush()
        }
    }

    private fun readUtf8String(din: DataInputStream): String {
        val len = din.readInt()
        if (len <= 0 || len > 4_000_000) return ""
        val buf = ByteArray(len)
        din.readFully(buf)
        return buf.toString(Charset.forName("UTF-8"))
    }

    private fun writeUtf8String(dout: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        dout.writeInt(bytes.size)
        dout.write(bytes)
    }

    private fun findCsv(context: Context): File? {
        val assetsDir: File = StorageUtils.getPublicAppDir(context, "assets")
        val f = File(assetsDir, "aliasmapping.csv")
        return if (f.exists() && f.isFile && f.canRead()) f else null
    }
}
