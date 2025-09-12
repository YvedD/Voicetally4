package com.yvesds.voicetally4.ui.schermen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.yvesds.voicetally4.databinding.FragmentOpstartSchermBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * StartScherm:
 * - Geen permissielogica meer; ALLE permissies + SAF-setup gebeuren in EersteSetupScherm.
 * - Dit scherm is nu enkel de start-UI (navigatie-acties / toasts).
 */
@AndroidEntryPoint
class StartScherm : Fragment() {

    private var _binding: FragmentOpstartSchermBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOpstartSchermBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnStartTelling.setOnClickListener {
            // TODO: Navigeer naar jouw telling-scherm wanneer beschikbaar
            Toast.makeText(requireContext(), "Start telling → TODO navigatie", Toast.LENGTH_SHORT).show()
        }

        binding.btnTellingen.setOnClickListener {
            // TODO: Navigeer naar overzicht eerdere tellingen
            Toast.makeText(requireContext(), "Tellingen (TODO navigatie)", Toast.LENGTH_SHORT).show()
        }

        binding.btnInstellingen.setOnClickListener {
            // TODO: Navigeer naar instellingen
            Toast.makeText(requireContext(), "Instellingen (TODO navigatie)", Toast.LENGTH_SHORT).show()
        }

        binding.btnAliassen.setOnClickListener {
            // TODO: Navigeer naar aliasbeheer
            Toast.makeText(requireContext(), "Aliassen (TODO navigatie)", Toast.LENGTH_SHORT).show()
        }

        binding.btnAfsluiten.setOnClickListener {
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
