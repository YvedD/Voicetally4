package com.yvesds.voicetally4.ui.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.WorkerThread
import com.yvesds.voicetally4.utils.io.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.measureNanoTime

/**
 * Warmt "Documents/VoiceTally4/binaries/aliases.vt4bin" op in RAM.
 *
 * - Eerst via MediaStore (RELATIVE_PATH) lezen → compatibel met jouw SAF/“Documents”-toestemming.
 * - Fallback naar File (indien nog mogelijk).
 * - Off-main (IO) en idempotent.
 */
object AliasesRepository {

    private const val TAG = "AliasesRepository"
    private const val BIN_NAME = "aliases.vt4bin"
    private const val APP_DIR = "VoiceTally4"
    private const val BIN_SUBDIR = "binaries"
    private const val REL_PATH = "Documents/$APP_DIR/$BIN_SUBDIR"

    @Volatile
    private var buffer: ByteBuffer? = null

    private val isLoading = AtomicBoolean(false)

    // -----------------------------------------
    // TEMP LOGGING — MAKKELIJK TE VERWIJDEREN
    // Zet op false om alle extra logs te dempen
    // -----------------------------------------
    private const val TEMP_DEBUG_LOGS: Boolean = true // <<< REMOVE_ME (of zet op false)
    private inline fun tlog(message: String) {
        if (TEMP_DEBUG_LOGS) Log.i("VT4.WARMUP", message)
    }
    // -----------------------------------------

    /**
     * Start een idempotente warm-up. Safe om meerdere keren te callen.
     * Result = hoeveelheid bytes in RAM (0 als niets gevonden).
     */
    suspend fun warmup(appContext: Context): Result<Int> = withContext(Dispatchers.IO) {
        buffer?.let {
            // TEMP LOG
            tlog("warmup: already loaded in RAM (${it.capacity()} bytes)")
            return@withContext Result.success(it.capacity())
        }
        if (!isLoading.compareAndSet(false, true)) {
            // TEMP LOG
            tlog("warmup: already loading; returning current=${buffer?.capacity() ?: 0} bytes")
            return@withContext Result.success(buffer?.capacity() ?: 0)
        }
        try {
            var loadedBytes: Int? = null
            var source: String? = null

            val elapsedNs = measureNanoTime {
                // 1) MediaStore (Q+) – werkt met jouw “Documents”-toestemming / RELATIVE_PATH.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    loadViaMediaStore(appContext)?.let { bb ->
                        buffer = bb
                        loadedBytes = bb.capacity()
                        source = "MediaStore($REL_PATH/$BIN_NAME)"
                        return@measureNanoTime
                    }
                }
                // 2) Fallback: probeer File (publieke Documents of app-private Documents via StorageUtils)
                loadViaFile(appContext)?.let { bb ->
                    buffer = bb
                    loadedBytes = bb.capacity()
                    source = "File"
                }
            }

            if (loadedBytes != null) {
                // TEMP LOG (duidelijk gemarkeerd)
                tlog(
                    "SUCCESS: aliases.vt4bin loaded: ${loadedBytes} bytes via $source " +
                            "in ${(elapsedNs / 1_000_000.0)} ms"
                )
                Log.i(TAG, "warmup: loaded ${loadedBytes} bytes via $source")
                return@withContext Result.success(loadedBytes!!)
            } else {
                Log.w(TAG, "warmup: bestand niet gevonden of niet leesbaar")
                // TEMP LOG
                tlog("WARN: aliases.vt4bin not found / unreadable (MediaStore & File failed)")
                return@withContext Result.success(0)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "warmup failed: ${t.message}", t)
            // TEMP LOG
            tlog("ERROR: warmup failed: ${t.message}")
            return@withContext Result.failure(t)
        } finally {
            isLoading.set(false)
        }
    }

    /** Read-only snapshot voor eventuele lezers later. */
    fun currentBuffer(): ByteBuffer? = buffer?.asReadOnlyBuffer()

    // --------------------------------------------------------------------
    // MediaStore pad (voorkeur bij Q+): RELATIVE_PATH = Documents/VoiceTally4/binaries
    // --------------------------------------------------------------------
    @WorkerThread
    private fun loadViaMediaStore(context: Context): ByteBuffer? {
        return try {
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.RELATIVE_PATH,
                MediaStore.Files.FileColumns.DISPLAY_NAME
            )
            val sel = "${MediaStore.Files.FileColumns.RELATIVE_PATH}=? AND ${MediaStore.Files.FileColumns.DISPLAY_NAME}=?"
            val args = arrayOf("$REL_PATH/", BIN_NAME)

            context.contentResolver.query(collection, projection, sel, args, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val id = c.getLong(idIdx)
                    val size = c.getLong(sizeIdx).coerceAtLeast(0L)
                    val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY, id)

                    context.contentResolver.openInputStream(uri)?.use { input ->
                        // TEMP LOG
                        tlog("read via MediaStore: id=$id, size=$size, uri=$uri")
                        return readStreamToDirectBuffer(input, sizeHint = size)
                    }
                }
            }
            null
        } catch (t: Throwable) {
            Log.w(TAG, "loadViaMediaStore: ${t.message}")
            // TEMP LOG
            tlog("loadViaMediaStore failed: ${t.message}")
            null
        }
    }

    // --------------------------------------------------------------------
    // File fallback (publieke Documents of app-private Documents)
    // --------------------------------------------------------------------
    @WorkerThread
    private fun loadViaFile(context: Context): ByteBuffer? {
        // a) Probeer via jouw StorageUtils map
        try {
            val dir: File? = StorageUtils.getPublicAppDir(context, BIN_SUBDIR)
            if (dir != null) {
                val f = File(dir, BIN_NAME)
                mapOrReadFile(f)?.let {
                    // TEMP LOG
                    tlog("read via File(StorageUtils): ${f.absolutePath}, bytes=${it.capacity()}")
                    return it
                }
            }
        } catch (t: Throwable) {
            // TEMP LOG
            tlog("loadViaFile(StorageUtils) failed: ${t.message}")
        }

        // b) Publieke Documents fallback
        return try {
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val f = File(File(docs, APP_DIR), BIN_SUBDIR).resolve(BIN_NAME)
            mapOrReadFile(f)?.also {
                // TEMP LOG
                tlog("read via File(Documents): ${f.absolutePath}, bytes=${it.capacity()}")
            }
        } catch (t: Throwable) {
            // TEMP LOG
            tlog("loadViaFile(Documents) failed: ${t.message}")
            null
        }
    }

    @WorkerThread
    private fun mapOrReadFile(f: File): ByteBuffer? {
        if (!f.exists() || !f.isFile || !f.canRead()) return null
        return try {
            // Probeer memory-map (snelst). Als het niet lukt, lees dan stream-based.
            FileInputStream(f).channel.use { ch ->
                ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size())
            }
        } catch (_: Throwable) {
            try {
                FileInputStream(f).use { input ->
                    readStreamToDirectBuffer(input, sizeHint = f.length().coerceAtLeast(0L))
                }
            } catch (_: Throwable) {
                null
            }
        }
    }

    // --------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------
    @WorkerThread
    private fun readStreamToDirectBuffer(input: java.io.InputStream, sizeHint: Long): ByteBuffer {
        // Allocate een direct buffer. Als sizeHint onbekend is, begin klein en groei.
        val initialCap = when {
            sizeHint in 1..(32L shl 20) -> sizeHint.toInt()           // ≤ 32MB
            sizeHint > (32L shl 20)     -> 32 shl 20                  // cap op 32MB; groeit indien nodig
            else                        -> 256 * 1024                 // 256KB start
        }

        var buf = ByteBuffer.allocateDirect(initialCap)
        val channel = Channels.newChannel(input)
        val tmp = ByteBuffer.allocate(64 * 1024)
        var total = 0

        while (true) {
            tmp.clear()
            val read = channel.read(tmp)
            if (read <= 0) break
            tmp.flip()

            if (buf.remaining() < read) {
                // groei
                val newCap = (buf.capacity() + read + (buf.capacity() / 2)).coerceAtMost(Int.MAX_VALUE)
                val grown = ByteBuffer.allocateDirect(newCap)
                buf.flip()
                grown.put(buf)
                buf = grown
            }
            buf.put(tmp)
            total += read
        }
        buf.flip()

        // TEMP LOG
        tlog("readStreamToDirectBuffer: total=$total bytes, finalCapacity=${buf.capacity()}")

        return buf
    }
}
