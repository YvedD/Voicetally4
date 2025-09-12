package com.yvesds.voicetally4.ui.domein

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Minimale ViewModel zonder dependencies.
 * (We gebruiken momenteel geen repository; CSV-inleescode zit in SoortSelectieScherm.)
 */
@HiltViewModel
class SoortSelectieViewModel @Inject constructor() : ViewModel()
