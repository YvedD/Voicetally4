package com.yvesds.voicetally4.data

import android.content.Context
import com.yvesds.voicetally4.utils.io.StorageUtils
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File

/**
 * Leest de spraak-aliassen bin (VT4ALIAS1) één keer in op background.
 * Doel: IO en disk page cache warmen, zodat SpeechParser later razendsnel is.
 *
 * Formaat volgens SpeechParser:
 *  magic: "VT4ALIAS1"
 *  records: [readUTF(alias), readUTF(id), readUTF(displayName)]
 */
object SpeechAliasBinWarmup {

    private const val MAGIC = "VT4ALIAS1"

    fun warm(context: Context) {
        val binDir = StorageUtils.getPublicAppDir(context, "binaries")
        val f = File(binDir, "aliasindex.bin")
        if (!f.exists() || !f.canRead()) return
        try {
            DataInputStream(BufferedInputStream(f.inputStream())).use { din ->
                // check magic
                val magic = ByteArray(MAGIC.length)
                din.readFully(magic)
                if (!magic.decodeToString().startsWith(MAGIC)) return
                // loop all records quickly; we hoeven niets te bewaren
                while (true) {
                    // readUTF() is DataInput’s eigen lengte+UTF mechanisme
                    din.readUTF() // alias
                    din.readUTF() // id
                    din.readUTF() // displayName
                }
            }
        } catch (_: Throwable) {
            // EOF of ander – maakt niet uit, doel is cache warmen
        }
    }
}
