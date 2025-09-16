package com.yvesds.voicetally4.utils.io

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Hulpfuncties voor opslagpaden en publiek wegschrijven.
 *
 * Ontwerp:
 * - Voorkeurspad voor jouw project: Documents/VoiceTally4[/subdir].
 * - Fallback (alleen als publiek echt niet lukt): app-private Documents/VoiceTally4[/subdir].
 * - Android 10+ (Q+): schrijven naar publieke *Documents* via MediaStore (RELATIVE_PATH).
 * - Android 9-: schrijven naar publieke *Documents* via File API (kan WRITE_EXTERNAL_STORAGE vereisen).
 *
 * **Belangrijk voor performance/UX**
 * - Roep de *suspend* varianten aan vanuit coroutines (I/O op Dispatchers.IO).
 * - De sync-varianten bestaan voor drop-in compat, maar doe ze niet op de main thread.
 */
object StorageUtils {

    private const val TAG = "StorageUtils"
    private const val PUBLIC_ROOT_DIR_NAME = "VoiceTally4"
    private const val MIME_JSON = "application/json"

    // ---------------------------------------------------------------------------------------------
    // Paden
    // ---------------------------------------------------------------------------------------------

    /**
     * Geeft de VoiceTally4-basis in Documents terug, met optionele submap.
     * Maakt de map(pen) aan indien ze nog niet bestaan.
     *
     * Let op: op Android 10+ met scoped storage is *directe* File-toegang tot publieke
     * Documents beperkt; gebruik dit vooral voor pad-weergave of voor bestanden die door
     * andere processen (of SAF) zijn geplaatst. Voor *schrijven* gebruik je de save*-functies.
     *
     * @param subdir bv. "serverdata" of null/"" voor de basis.
     */
    @JvmStatic
    fun getPublicAppDir(context: Context, subdir: String? = null): File {
        // Publieke Documents/VoiceTally4
        val publicBase = getPublicDocumentsVoiceTallyDir()

        // App-private Documents/VoiceTally4 (fallback)
        val appDocsBase = File(
            context.applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            PUBLIC_ROOT_DIR_NAME
        )

        // Gebruik publieke map als die al bestaat; anders de app-private (en maak die aan)
        val base = if (publicBase.exists()) publicBase else appDocsBase.apply { mkdirs() }

        val target = if (subdir.isNullOrBlank()) base else File(base, subdir)
        if (!target.exists()) {
            target.mkdirs()
        }
        return target
    }

    // ---------------------------------------------------------------------------------------------
    // Publiek wegschrijven (String / JSON) — **gebruik bij voorkeur de async-variant**
    // ---------------------------------------------------------------------------------------------

    /**
     * Schrijft JSON-/tekstinhoud publiek naar: Documents/<relativeSubdir>/<fileName>.
     *
     * Android 10+ (Q+): via MediaStore met RELATIVE_PATH (geen extra permissies nodig).
     * Android 9-      : via File API naar publieke Documents (kan WRITE_EXTERNAL_STORAGE vereisen).
     *
     * @return De publieke Uri op Android 10+; op oudere versies null (bestand staat dan als File).
     */
    @JvmStatic
    fun saveJsonToPublicDocuments(
        context: Context,
        relativeSubdir: String,
        fileName: String,
        content: String
    ): Uri? = saveStringToPublicDocuments(context, relativeSubdir, fileName, content)

    /**
     * Alias-signatuur die elders in de app gebruikt wordt.
     * Schrijft willekeurige tekst (JSON/…) naar Documents/<subDir>/<fileName>.
     *
     * **Niet op main-thread aanroepen**; gebruik zo mogelijk [saveStringToPublicDocumentsAsync].
     */
    @JvmStatic
    fun saveStringToPublicDocuments(
        context: Context,
        subDir: String,
        fileName: String,
        content: String
    ): Uri? {
        val ctx = context.applicationContext
        val relativePath = "Documents/${subDir.trimStart('/')}"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // MediaStore-pad
            try {
                deleteIfExistsQ(ctx, relativePath, fileName)
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val values = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.Files.FileColumns.MIME_TYPE, MIME_JSON)
                    put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativePath)
                }
                val uri = ctx.contentResolver.insert(collection, values)
                if (uri != null) {
                    ctx.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(content.toByteArray(Charsets.UTF_8))
                        os.flush()
                    }
                } else {
                    Log.w(TAG, "Kon geen Uri aanmaken in MediaStore (null)")
                }
                uri
            } catch (t: Throwable) {
                Log.e(TAG, "Public save via MediaStore mislukt: ${t.message}", t)
                // Laatste redmiddel: probeer app-private als absoluut noodzakelijke fallback
                writeToAppPrivateDocuments(ctx, subDir, fileName, content)
                null
            }
        } else {
            // Pre-Q: direct File-schrijf naar publieke Documents
            try {
                val base = File(getPublicDocumentsVoiceTallyDir(), subDir).apply { mkdirs() }
                val outFile = File(base, fileName)
                outFile.writeText(content, Charsets.UTF_8)
                null
            } catch (t: Throwable) {
                Log.e(TAG, "Public save (pre-Q) mislukt: ${t.message}", t)
                // Fallback naar app-private
                writeToAppPrivateDocuments(ctx, subDir, fileName, content)
                null
            }
        }
    }

    /**
     * Asynchrone variant die I/O op [Dispatchers.IO] uitvoert.
     */
    @JvmStatic
    suspend fun saveStringToPublicDocumentsAsync(
        context: Context,
        subDir: String,
        fileName: String,
        content: String
    ): Uri? = withContext(Dispatchers.IO) {
        saveStringToPublicDocuments(context, subDir, fileName, content)
    }

    // ---------------------------------------------------------------------------------------------
    // Intern
    // ---------------------------------------------------------------------------------------------

    private fun writeToAppPrivateDocuments(
        context: Context,
        relativeSubdir: String,
        fileName: String,
        content: String
    ) {
        val base = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "$PUBLIC_ROOT_DIR_NAME/${relativeSubdir.trimStart('/')}"
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
        return File(documentsDir, PUBLIC_ROOT_DIR_NAME)
    }

    /**
     * Android 10+: verwijdert bestaande entry met zelfde RELATIVE_PATH + DISPLAY_NAME.
     */
    private fun deleteIfExistsQ(context: Context, relativePath: String, fileName: String) {
        try {
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val sel =
                "${MediaStore.Files.FileColumns.RELATIVE_PATH}=? AND ${MediaStore.Files.FileColumns.DISPLAY_NAME}=?"
            val args = arrayOf(relativePath, fileName)
            context.contentResolver
                .query(collection, arrayOf(MediaStore.Files.FileColumns._ID), sel, args, null)
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
