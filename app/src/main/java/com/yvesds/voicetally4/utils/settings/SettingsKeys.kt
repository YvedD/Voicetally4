package com.yvesds.voicetally4.utils.settings

/**
 * Centrale sleuteldefinities (prefs & instellingen).
 * - Geen backward-compat nodig: we bouwen VT4 "from scratch".
 */
object SettingsKeys {
    // <-- nodig voor StartActiviteit
    const val KEY_SETUP_DONE = "setup_done"
    // Algemene prefs (bestaand gebruik in project)
    const val PREFS_NAME: String = "voicetally_prefs"

    // Upload/telling prefs (al in gebruik in MetadataScherm)
    const val UPLOAD_PREFS: String = "upload_prefs"
    const val KEY_LAST_ONLINE_ID: String = "last_onlineid"

    // Speech instellingen
    const val SPEECH_PREFS: String = "speech_prefs"
    const val KEY_SPEECH_SILENCE_TIMEOUT_MS: String = "speech_silence_timeout_ms"
    const val DEFAULT_SPEECH_SILENCE_TIMEOUT_MS: Int = 2000 // 2s (aanpasbaar via SettingsFragment later)

    // Bestanden/folders (publieke documenten)
    const val DIR_EXPORTS: String = "exports"
    const val DIR_SERVERDATA: String = "serverdata"
    const val DIR_TELLINGEN: String = "serverdata/tellingen"
    const val DIR_ASSETS: String = "assets"
    const val DIR_BINARIES: String = "binaries"

    // Aliasmapping locatie
    const val FILE_ALIAS_CSV: String = "aliasmapping.csv" // /Documents/VoiceTally4/assets/aliasmapping.csv
    // Mogelijke binaire caches (we accepteren beide namen voor backward compat)
    const val FILE_ALIASES_BIN_A: String = "aliases.bin"
    const val FILE_ALIASES_BIN_B: String = "aliasmapping.bin"
}
