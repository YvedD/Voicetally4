package com.yvesds.voicetally4.ui.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Backwards-compat shim:
 * Bestond al als loader/builder voor canonical→displayName.
 * Nu delegeren we naar UnifiedAliasStore v2.
 *
 * Je kan callsites laten staan — dit roept de nieuwe store aan.
 */
object AliasDisplayNamesLoader {

    /** Zorgt dat de store klaar is (bouwt indien nodig) en geeft (canonical, displayName) terug. */
    suspend fun loadDisplayNames(context: Context): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            UnifiedAliasStore.ensureReady(context)
            // map terug als (canonical, displayName)
            UnifiedAliasStore.allTiles().map { it.canonical to it.displayName }
        }
}
