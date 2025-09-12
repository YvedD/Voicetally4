package com.yvesds.voicetally4.ui.schermen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.yvesds.voicetally4.R
import com.yvesds.voicetally4.StartActiviteit
import com.yvesds.voicetally4.databinding.FragmentOpstartSchermBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Opstartscherm met 5 grote knoppen.
 * Vraagt permissies (RECORD_AUDIO + FINE/COARSE LOCATION) slechts éénmalig.
 */
@AndroidEntryPoint
class StartScherm : Fragment(R.layout.fragment_opstart_scherm) {

    private var _binding: FragmentOpstartSchermBinding? = null
    private val binding get() = _binding!!

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val audio = result[Manifest.permission.RECORD_AUDIO] == true
        val fine  = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse= result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val okLoc = fine || coarse

        val msg = "Permissies → " +
                (if (audio) "🎤 Audio OK" else "🎤 Audio geweigerd") +
                " / " +
                (if (okLoc) "📍 Locatie OK" else "📍 Locatie geweigerd")
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()

        // Markeer dat we de permissies reeds éénmaal gevraagd hebben
        requireContext().getSharedPreferences(StartActiviteit.PREFS_NAME, 0)
            .edit()
            .putBoolean(StartActiviteit.KEY_PERMISSIONS_DONE, true)
            .apply()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentOpstartSchermBinding.bind(view)

        // Eénmalige permissie-flow
        maybeRequestPermissionsOnce()

        // Knoppen (nog geen navigatie-acties koppelen)
        binding.btnStartTelling.setOnClickListener { /* TODO later */ }
        binding.btnTellingen.setOnClickListener  { /* TODO later */ }
        binding.btnInstellingen.setOnClickListener { /* TODO later */ }
        binding.btnAliassen.setOnClickListener   { /* TODO later */ }
        binding.btnAfsluiten.setOnClickListener  { requireActivity().finish() }
    }

    private fun maybeRequestPermissionsOnce() {
        val prefs = requireContext().getSharedPreferences(StartActiviteit.PREFS_NAME, 0)
        val alreadyAsked = prefs.getBoolean(StartActiviteit.KEY_PERMISSIONS_DONE, false)
        val needs = mutableListOf<String>()

        // Alleen vragen wat nog niet verleend is
        if (!isGranted(Manifest.permission.RECORD_AUDIO)) needs += Manifest.permission.RECORD_AUDIO
        val locGranted = isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
                isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!locGranted) {
            needs += Manifest.permission.ACCESS_FINE_LOCATION
            needs += Manifest.permission.ACCESS_COARSE_LOCATION
        }

        if (!alreadyAsked && needs.isNotEmpty()) {
            permissionsLauncher.launch(needs.toTypedArray())
        } else if (alreadyAsked || needs.isEmpty()) {
            // niks te doen
        }
    }

    private fun isGranted(perm: String): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), perm) == PackageManager.PERMISSION_GRANTED

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
