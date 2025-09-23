package com.yvesds.voicetally4.ui.schermen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.yvesds.voicetally4.databinding.FragmentTallySchermBinding
import com.yvesds.voicetally4.ui.adapters.SpeechLogAdapter
import com.yvesds.voicetally4.ui.adapters.TallyAdapter
import com.yvesds.voicetally4.ui.shared.SharedSpeciesViewModel
import com.yvesds.voicetally4.ui.tally.TallyViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * VT3-look & feel in VT4:
 * - Logvenster boven (met border in layout)
 * - 3 knoppen: Save / Add / Export
 * - Tellers onderaan (displayName = tile name)
 *
 * Belangrijk: TallyViewModel is no-arg en wordt gevoed vanuit SharedSpeciesViewModel.
 */
class TallyScherm : Fragment() {

    private var _binding: FragmentTallySchermBinding? = null
    private val binding get() = _binding!!

    private val sharedVm: SharedSpeciesViewModel by activityViewModels()
    private val tallyVm: TallyViewModel by viewModels() // no-arg VM

    private lateinit var tallyAdapter: TallyAdapter
    private lateinit var logAdapter: SpeechLogAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTallySchermBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Log venster (bovenaan)
        logAdapter = SpeechLogAdapter()
        binding.recyclerViewSpeechLog.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = logAdapter
            setHasFixedSize(false)
        }
        logAdapter.setAll(listOf("ℹ️ Klaar voor telling…"))

        // Tellers
        tallyAdapter = TallyAdapter(
            onIncrement = { canonical -> tallyVm.increment(canonical) },
            onDecrement = { canonical -> tallyVm.decrement(canonical) },
            onReset     = { canonical -> tallyVm.reset(canonical) }
        )
        binding.recyclerViewTally.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = tallyAdapter
            setHasFixedSize(true)
        }

        // Koppel Shared → Tally
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    sharedVm.selectedCanonicals.collectLatest { canonicals ->
                        val displayMap = sharedVm.displayNames.value
                        tallyVm.setSelection(canonicals, displayMap)
                    }
                }
                launch {
                    sharedVm.displayNames.collectLatest { displayMap ->
                        val canonicals = sharedVm.selectedCanonicals.value
                        tallyVm.setSelection(canonicals, displayMap)
                    }
                }
            }
        }

        // Observe items → adapter
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    tallyVm.items.collectLatest { list ->
                        tallyAdapter.submitList(list)
                    }
                }
            }
        }

        // Knoppen
        binding.buttonEndSession.setOnClickListener {
            logAdapter.add("💾 Save: nog te koppelen aan export/resultaten.")
            Toast.makeText(requireContext(), "Opslaan komt er aan…", Toast.LENGTH_SHORT).show()
        }
        binding.buttonAddSpecies.setOnClickListener {
            logAdapter.add("➕ Add species: open Soortselectie (ga terug).")
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.buttonExportObservations.setOnClickListener {
            logAdapter.add("⤴️ Export: nog te koppelen aan export-logica.")
            Toast.makeText(requireContext(), "Export komt er aan…", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
