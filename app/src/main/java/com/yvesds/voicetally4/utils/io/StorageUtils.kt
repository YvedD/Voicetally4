package com.yvesds.voicetally4.utils.io

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * Hulpfuncties voor opslagpaden en publiek wegschrijven.
 *
 * - Voorkeurspad voor jouw project:  Documents/VoiceTally4[/subdir]
 * - Fallback (alleen als publiek echt niet lukt): app-private Documents/VoiceTally4[/subdir]
 */
object StorageUtils {

    private const val TAG = "StorageUtils"

    /**
     * Geeft de VoiceTally4-basis in Documents terug, met optionele submap.
     * Maakt de map(pen) aan indien ze nog niet bestaan.
     *
     * @param subdir bv. "serverdata" of null/"" voor de basis.
     */
    @JvmStatic
    fun getPublicAppDir(context: Context, subdir: String? = null): File {
        // Publieke Documents/VoiceTally4
        val publicBase = getPublicDocumentsVoiceTallyDir()

        // App-private Documents/VoiceTally4 (fallback)
        val appDocsBase = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "VoiceTally4"
        )

        // Gebruik publieke map als die al bestaat; anders de app-private (en maak die aan)
        val base = if (publicBase.exists()) publicBase else appDocsBase.apply { mkdirs() }

        val target = if (subdir.isNullOrBlank()) base else File(base, subdir)
        if (!target.exists()) {
            target.mkdirs()
        }
        return target
    }

    /**
     * Schrijft JSON-tekst publiek naar:
     *   Documents/<relativeSubdir>/<fileName>
     *
     * Android 10+ (Q+): via MediaStore met RELATIVE_PATH (geen extra permissies nodig).
     * Android 9 en lager: via File API naar de publieke Documents-map (kan WRITE_EXTERNAL_STORAGE vereisen).
     *
     * @return De publieke Uri op Android 10+; op oudere versies null (bestand staat dan als File op schijf).
     */
    @JvmStatic
    fun saveJsonToPublicDocuments(
        context: Context,
        relativeSubdir: String,
        fileName: String,
        content: String
    ): Uri? {
        val relativePath = "Documents/$relativeSubdir"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // Verwijder eventueel bestaand exemplaar met dezelfde naam/relatief pad
                deleteIfExistsQ(context, relativePath, fileName)

                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val values = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.Files.FileColumns.MIME_TYPE, "application/json")
                    put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativePath)
                }
                val uri = context.contentResolver.insert(collection, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(content.toByteArray(Charsets.UTF_8))
                        os.flush()
                    }
                } else {
                    Log.w(TAG, "Kon geen Uri aanmaken in MediaStore (null terug)")
                }
                uri
            } catch (t: Throwable) {
                Log.e(TAG, "Public save via MediaStore mislukt: ${t.message}", t)
                // Laatste redmiddel: probeer app-private als absoluut noodzakelijke fallback
                writeToAppPrivateDocuments(context, relativeSubdir, fileName, content)
                null
            }
        } else {
            // Oudere Android-versies: direct naar publieke Documents
            try {
                val base = File(getPublicDocumentsVoiceTallyDir(), relativeSubdir).apply { mkdirs() }
                val outFile = File(base, fileName)
                outFile.writeText(content, Charsets.UTF_8)
                null
            } catch (t: Throwable) {
                Log.e(TAG, "Public save (pre-Q) mislukt: ${t.message}", t)
                // Fallback naar app-private
                writeToAppPrivateDocuments(context, relativeSubdir, fileName, content)
                null
            }
        }
    }

    /**
     * Alias met de signatuur die elders in de app al gebruikt wordt.
     * Schrijft willekeurige tekst (JSON/…) naar Documents/<subDir>/<fileName>.
     */
    @JvmStatic
    fun saveStringToPublicDocuments(
        context: Context,
        subDir: String,
        fileName: String,
        content: String
    ): Uri? = saveJsonToPublicDocuments(
        context = context,
        relativeSubdir = subDir,
        fileName = fileName,
        content = content
    )

    // --- intern: helpers ---

    private fun writeToAppPrivateDocuments(
        context: Context,
        relativeSubdir: String,
        fileName: String,
        content: String
    ) {
        val base = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "VoiceTally4/${relativeSubdir.trimStart('/')}"
        ).apply { mkdirs() }
        val outFile = File(base, fileName)
        outFile.writeText(content, Charsets.UTF_8)
        Log.w(TAG, "Weggeschreven naar app-private (fallback): ${outFile.absolutePath}")
    }

    private fun getPublicDocumentsVoiceTallyDir(): File {
        val documentsDir: File = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            @Suppress("DEPRECATION")
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        } else {
            @Suppress("DEPRECATION")
            File(Environment.getExternalStorageDirectory(), "Documents")
        }
        return File(documentsDir, "VoiceTally4")
    }

    /**
     * Android 10+: verwijdert bestaande entry met zelfde RELATIVE_PATH + DISPLAY_NAME.
     */
    private fun deleteIfExistsQ(context: Context, relativePath: String, fileName: String) {
        try {
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val sel = "${MediaStore.Files.FileColumns.RELATIVE_PATH}=? AND ${MediaStore.Files.FileColumns.DISPLAY_NAME}=?"
            val args = arrayOf(relativePath, fileName)
            context.contentResolver.query(collection, arrayOf(MediaStore.Files.FileColumns._ID), sel, args, null)
                ?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val uri = Uri.withAppendedPath(collection, id.toString())
                        context.contentResolver.delete(uri, null, null)
                    }
                }
        } catch (t: Throwable) {
            Log.w(TAG, "deleteIfExistsQ: $relativePath/$fileName: ${t.message}")
        }
    }
}
