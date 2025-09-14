package com.yvesds.voicetally4.utils.io

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object StorageUtils {

    /**
     * Schrijf JSON publiek in de Documenten-map via MediaStore (Android 10+),
     * naar: Documents/<relativeSubdir>/<fileName>
     *
     * ≤ Android 9: schrijft rechtstreeks naar de publieke Documenten-map
     * (kan WRITE_EXTERNAL_STORAGE vereisen op oude toestellen).
     *
     * @return het publieke Uri (Android 10+) of null (legacy ≤ Android 9).
     */
    fun saveJsonToPublicDocuments(
        context: Context,
        relativeSubdir: String, // bv. "VoiceTally4/serverdata"
        fileName: String,       // bv. "codes.json"
        content: String
    ): Uri? {
        val mime = "application/json"
        val relPath = Environment.DIRECTORY_DOCUMENTS + "/" + relativeSubdir.trimStart('/')

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Scoped storage via MediaStore
            val resolver = context.contentResolver

            // Probeer bestaande entry met dezelfde RELATIVE_PATH + DISPLAY_NAME te vinden (overwrite via "rwt")
            val existing = resolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.RELATIVE_PATH
                ),
                "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf("$relPath/", fileName),
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(0)
                    MediaStore.Files.getContentUri("external", id)
                } else null
            }

            val targetUri = existing ?: run {
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                }
                resolver.insert(MediaStore.Files.getContentUri("external"), cv)
            }

            if (targetUri != null) {
                resolver.openOutputStream(targetUri, "rwt").use { os ->
                    requireNotNull(os) { "Kon OutputStream niet openen voor $targetUri" }
                    os.writer().use { it.write(content) }
                }
            }
            return targetUri
        } else {
            // Legacy: directe schrijf in publieke Documenten
            @Suppress("DEPRECATION")
            val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val dir = File(base, relativeSubdir)
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, fileName)
            outFile.writeText(content)
            return null
        }
    }
}
