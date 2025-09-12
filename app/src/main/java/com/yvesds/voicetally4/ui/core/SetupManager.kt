package com.yvesds.voicetally4.ui.core

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

/**
 * Verantwoordelijk voor:
 * - Bewaren & ophalen van de gekozen Documents-root (SAF Tree URI)
 * - Checken/aanmaken van VoiceTally4 + submappen
 * - Controleren of setup volledig is
 */
class SetupManager(
    private val context: Context,
    private val prefs: SharedPreferences
) {

    companion object {
        private const val PREFS_KEY_TREE_URI = "pref_documents_tree_uri"
        private const val DIR_APP_ROOT = "VoiceTally4"
        private val REQUIRED_SUBDIRS = listOf("assets", "exports", "serverdata")
    }

    fun getPersistedTreeUri(): Uri? {
        val str = prefs.getString(PREFS_KEY_TREE_URI, null) ?: return null
        return Uri.parse(str)
    }

    fun savePersistedTreeUri(uri: Uri) {
        prefs.edit().putString(PREFS_KEY_TREE_URI, uri.toString()).apply()
    }

    fun hasPersistedPermission(uri: Uri): Boolean {
        val pm = context.contentResolver.persistedUriPermissions
        return pm.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
    }

    /**
     * Controle: Bestaan alle mappen, en hebben we nog geldige SAF-permissie?
     * Als de URI ontbreekt of permissie niet meer bestaat → false.
     */
    fun isSetupComplete(): Boolean {
        val treeUri = getPersistedTreeUri() ?: return false
        if (!hasPersistedPermission(treeUri)) return false

        val docsTree = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        val appRoot = docsTree.findFile(DIR_APP_ROOT)?.takeIf { it.isDirectory } ?: return false

        // Alle verplichte subfolders aanwezig?
        val names: Set<String?> = appRoot.listFiles().map { it.name }.toSet()
        return REQUIRED_SUBDIRS.all { names.contains(it) }
    }

    /**
     * Maakt (indien nodig) VoiceTally4 + submappen aan.
     * Vooraf: zorg dat je WRITE-permission op de treeUri hebt (via SAF + persist).
     */
    fun ensureFolderStructure(treeUri: Uri): Result<Unit> {
        val docsTree = DocumentFile.fromTreeUri(context, treeUri)
            ?: return Result.failure(IllegalStateException("Ongeldige Documents tree URI"))

        val appRoot = docsTree.findOrCreateDirectory(DIR_APP_ROOT)
            ?: return Result.failure(IllegalStateException("Kon $DIR_APP_ROOT niet aanmaken"))

        for (dir in REQUIRED_SUBDIRS) {
            val created = appRoot.findOrCreateDirectory(dir)
            if (created == null) {
                return Result.failure(IllegalStateException("Kon submap $dir niet aanmaken"))
            }
        }
        return Result.success(Unit)
    }

    /**
     * Bouw een INIT-hint naar /Documents om de picker daar te laten starten (optioneel).
     */
    fun buildInitialDocumentsUri(): Uri =
        DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Documents"
        )

    /**
     * Persist de door de gebruiker verleende permissie (gebruik de flags uit het intent-resultaat).
     */
    fun persistUriPermission(uri: Uri, grantedFlags: Int) {
        val mask = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val flags = grantedFlags and mask
        try {
            // Probeer met de echt verleende flags
            if (flags != 0) {
                context.contentResolver.takePersistableUriPermission(uri, flags)
                return
            }
        } catch (_: SecurityException) {
            // val terugvallen hieronder
        }
        // Fallback: probeer klassiek READ|WRITE (werkt in de meeste gevallen ook)
        try {
            context.contentResolver.takePersistableUriPermission(uri, mask)
        } catch (_: SecurityException) {
            // Laat isSetupComplete() dit later opvangen als permissie zou wegvallen
        }
    }
}

/* ---------------------- helpers ---------------------- */

private fun DocumentFile.findOrCreateDirectory(name: String): DocumentFile? {
    val existing = this.findFile(name)
    if (existing != null && existing.isDirectory) return existing
    return this.createDirectory(name)
}
