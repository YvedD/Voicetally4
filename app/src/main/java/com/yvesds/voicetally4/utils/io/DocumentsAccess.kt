package com.yvesds.voicetally4.utils.io

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile

/**
 * Beheer van een persistente SAF-toestemming op de "Documents" (of VoiceTally4) map.
 *
 * Flow:
 *  1) UI start ACTION_OPEN_DOCUMENT_TREE en laat user "Documents" of "Documents/VoiceTally4" kiezen.
 *  2) De onActivityResult geeft een treeUri terug -> call DocumentsAccess.persistTreeUri(context, uri).
 *  3) Vanaf dan kunnen we bestanden onder VoiceTally4 lezen/schrijven via DocumentFile.
 *
 * We accepteren zowel "Documents" als "Documents/VoiceTally4" als root.
 * Alle paden die we hier gebruiken zijn relatief t.o.v. VoiceTally4.
 */
object DocumentsAccess {

    private const val TAG = "DocumentsAccess"
    private const val PREFS = "documents_access_prefs"
    private const val KEY_TREE_URI = "tree_uri"

    private const val VOICETALLY_DIR = "VoiceTally4"

    fun hasPersistedUri(context: Context): Boolean = getPersistedUri(context) != null

    fun getPersistedUri(context: Context): Uri? {
        val uri = prefs(context).getString(KEY_TREE_URI, null) ?: return null
        return Uri.parse(uri)
    }

    fun persistTreeUri(context: Context, treeUri: Uri) {
        // Persisteren
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (t: Throwable) {
            Log.w(TAG, "takePersistableUriPermission: ${t.message}")
        }
        // Opslaan
        prefs(context).edit().putString(KEY_TREE_URI, treeUri.toString()).apply()
        Log.i(TAG, "Persisted tree URI: $treeUri")
    }

    fun clearPersistedUri(context: Context) {
        val uri = getPersistedUri(context)
        if (uri != null) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Throwable) { /* ignore */ }
        }
        prefs(context).edit().remove(KEY_TREE_URI).apply()
    }

    /**
     * Vind de VoiceTally4-root onder de gekozen tree (user kon "Documents" of "Documents/VoiceTally4" kiezen).
     * - Als de tree *zelf* "VoiceTally4" is -> return die
     * - Als de tree "Documents" is -> zoek/maak kind "VoiceTally4"
     * - Anders probeer ook nog te vinden
     */
    fun getVoiceTallyRoot(context: Context): DocumentFile? {
        val treeUri = getPersistedUri(context) ?: return null
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null

        // Als de user direct VoiceTally4 koos
        if (root.name.equals(VOICETALLY_DIR, ignoreCase = true)) {
            return root
        }

        // Als de user "Documents" koos, probeer VoiceTally4 als child
        val child = root.findFile(VOICETALLY_DIR)
        if (child != null && child.isDirectory) return child

        // Probeer te maken als het ontbreekt
        return root.createDirectory(VOICETALLY_DIR)
    }

    /** Haal/maak subdir onder VoiceTally4 (bijv. "assets", "binaries"). */
    fun getOrCreateSubdir(context: Context, subdir: String): DocumentFile? {
        val vtRoot = getVoiceTallyRoot(context) ?: return null
        val existing = vtRoot.findFile(subdir)
        if (existing != null && existing.isDirectory) return existing
        return vtRoot.createDirectory(subdir)
    }

    /** Vind een bestand relatief onder VoiceTally4 (bijv. "assets/aliasmapping.csv"). */
    fun resolveRelativeFile(context: Context, relativePath: String): DocumentFile? {
        val vtRoot = getVoiceTallyRoot(context) ?: return null
        val parts = relativePath.split('/').filter { it.isNotBlank() }
        var cur: DocumentFile = vtRoot
        for (i in parts.indices) {
            val name = parts[i]
            val last = (i == parts.lastIndex)
            val next = cur.findFile(name)
            if (next == null) {
                // niet gevonden
                return null
            } else {
                if (last) return next
                if (!next.isDirectory) return null
                cur = next
            }
        }
        return null
    }

    /** Zorg dat een bestand bestaat (aanmaken/overschrijven) onder VoiceTally4. */
    fun createOrReplaceFile(context: Context, relativePath: String, mime: String): DocumentFile? {
        val vtRoot = getVoiceTallyRoot(context) ?: return null
        val parts = relativePath.split('/').filter { it.isNotBlank() }
        var cur: DocumentFile = vtRoot
        for (i in 0 until parts.size - 1) {
            val seg = parts[i]
            val sub = cur.findFile(seg) ?: cur.createDirectory(seg) ?: return null
            if (!sub.isDirectory) return null
            cur = sub
        }
        val filename = parts.last()
        // verwijder bestaand
        cur.findFile(filename)?.delete()
        return cur.createFile(mime, filename)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
