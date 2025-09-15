package com.yvesds.voicetally4.ui.schermen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.yvesds.voicetally4.R
import com.yvesds.voicetally4.databinding.FragmentOpstartSchermBinding

/**
 * Lichtgewicht startscherm:
 * - GEEN vooraf inladen van soorten/aliassen of andere I/O.
 * - Geen lifecycle coroutines nodig hier.
 * - Snel navigeren naar MetadataScherm.
 */
class StartScherm : Fragment() {

    private var _binding: FragmentOpstartSchermBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentOpstartSchermBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Start onmiddellijk naar MetadataScherm — geen preloading/leesacties meer.
        binding.btnStartTelling.setOnClickListener {
            findNavController().navigate(R.id.action_opstartScherm_to_metadataScherm)
        }

        // Overige knoppen laten we voorlopig als placeholders.
        binding.btnTellingen.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.tellingen), Toast.LENGTH_SHORT).show()
        }
        binding.btnInstellingen.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.instellingen), Toast.LENGTH_SHORT).show()
        }
        binding.btnAliassen.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.aliassen), Toast.LENGTH_SHORT).show()
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
