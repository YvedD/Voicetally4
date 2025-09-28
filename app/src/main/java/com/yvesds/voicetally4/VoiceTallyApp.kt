package com.yvesds.voicetally4

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Laat Application init licht: geen I/O of SAF toegang hier.
 * UnifiedAliasStore wordt lazy geinitialiseerd door de schermen
 * die het nodig hebben, nadat de gebruiker de SAF-toestemming gaf.
 */
@HiltAndroidApp
class VoiceTallyApp : Application()
