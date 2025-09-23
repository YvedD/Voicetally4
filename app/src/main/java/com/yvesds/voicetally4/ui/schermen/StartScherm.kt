package com.yvesds.voicetally4.ui.schermen

import android.os.Bundle
import android.util.Log
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("VT4.StartScherm", "onCreate() ${hashCode()}")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.i("VT4.StartScherm", "onCreateView() ${hashCode()}")
        _binding = FragmentOpstartSchermBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.i("VT4.StartScherm", "onViewCreated() ${hashCode()}, saved=$savedInstanceState")
        super.onViewCreated(view, savedInstanceState)

        // Navigatie naar MetadataScherm; geen I/O of parsing hier
        binding.btnStartTelling.setOnClickListener {
            findNavController().navigate(R.id.action_opstartScherm_to_metadataScherm)
        }

        // Lichte toasts (geen blocking)
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
        Log.i("VT4.StartScherm", "onDestroyView() ${hashCode()}")
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        Log.i("VT4.StartScherm", "onDestroy() ${hashCode()}")
        super.onDestroy()
    }
}
