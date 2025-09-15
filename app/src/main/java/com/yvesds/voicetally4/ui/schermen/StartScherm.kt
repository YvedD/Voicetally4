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
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
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

        // Zo snel mogelijk naar MetadataScherm; geen I/O of parsing meer hier.
        binding.btnStartTelling.setOnClickListener {
            findNavController().navigate(R.id.action_opstartScherm_to_metadataScherm)
        }

        // De rest houdt voorlopig de bestaande (lichte) toasts.
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
