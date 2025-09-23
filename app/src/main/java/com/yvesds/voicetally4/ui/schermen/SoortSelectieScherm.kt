package com.yvesds.voicetally4.ui.schermen

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.material.snackbar.Snackbar
import com.yvesds.voicetally4.R
import com.yvesds.voicetally4.databinding.FragmentSoortSelectieSchermBinding
import com.yvesds.voicetally4.ui.adapters.AliasTileAdapter
import com.yvesds.voicetally4.ui.data.SoortAlias
import com.yvesds.voicetally4.ui.domein.SoortSelectieViewModel
import com.yvesds.voicetally4.ui.domein.SoortSelectieViewModel.UiState
import com.yvesds.voicetally4.ui.shared.SharedSpeciesViewModel
import com.yvesds.voicetally4.utils.io.DocumentsAccess
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SoortSelectieScherm : Fragment() {

    private var _binding: FragmentSoortSelectieSchermBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SoortSelectieViewModel by viewModels()
    private val sharedVm: SharedSpeciesViewModel by activityViewModels()

    private lateinit var adapter: AliasTileAdapter

    // ACTION_OPEN_DOCUMENT_TREE voor Documents (of VoiceTally4)
    private val pickTreeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = res.data?.data
            if (uri != null) {
                // Persistente toestemming opslaan
                DocumentsAccess.persistTreeUri(requireContext(), uri)
                Snackbar.make(binding.root, "Documenten-map gekoppeld.", Snackbar.LENGTH_SHORT).show()
                // Opnieuw laden
                viewModel.forceReload()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSoortSelectieSchermBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Flexbox tiles
        val lm = FlexboxLayoutManager(requireContext()).apply {
            flexDirection = FlexDirection.ROW
            flexWrap = FlexWrap.WRAP
            justifyContent = JustifyContent.FLEX_START
        }
        binding.recycler.layoutManager = lm
        binding.recycler.setHasFixedSize(true)
        binding.recycler.itemAnimator = null
        binding.recycler.setItemViewCacheSize(64)
        binding.recycler.recycledViewPool.setMaxRecycledViews(0, 128)

        adapter = AliasTileAdapter(
            isSelected = { tileName -> viewModel.isSelected(tileName) },
            onToggle = { tileName ->
                viewModel.toggleSelection(tileName)
                // Tiles hertekenen zodat de "checked" state klopt
                binding.recycler.post { adapter.notifyDataSetChanged() }
                showSelectionHint()
            }
        )
        binding.recycler.adapter = adapter

        // State observeren
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { renderState(it) } }
            }
        }

        // Start load (CSV of binaire cache)
        viewModel.loadAliases()

        // OK: schrijf selectie naar gedeelde VM en navigeer naar Tally
        binding.btnOk.setOnClickListener {
            val (chosenCanonical, displayMap) = buildSelectionAndDisplayMap()
            sharedVm.setSelectedSpecies(chosenCanonical)
            if (displayMap.isNotEmpty()) sharedVm.setDisplayNames(displayMap)

            val msg = if (chosenCanonical.isEmpty()) {
                getString(R.string.chosen_species_none)
            } else {
                getString(R.string.chosen_species_prefix, chosenCanonical.joinToString(", "))
            }
            Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()

            findNavController().navigate(R.id.action_soortSelectieScherm_to_tallyScherm)
        }
    }

    private fun renderState(state: UiState) {
        when (state) {
            is UiState.Loading -> {
                binding.emptyView.isVisible = false
                binding.btnOk.isEnabled = false
            }
            is UiState.Success -> {
                val tiles: List<SoortAlias> = state.items.map {
                    SoortAlias(
                        soortId = it.soortId,
                        canonical = it.canonical,
                        tileName = it.tileName,
                        aliases = it.aliases
                    )
                }
                adapter.submitList(tiles)
                binding.emptyView.isVisible = tiles.isEmpty()
                binding.btnOk.isEnabled = true
                showSelectionHint()
            }
            is UiState.Error -> {
                adapter.submitList(emptyList())
                binding.emptyView.isVisible = true
                binding.btnOk.isEnabled = false

                val actionLabel = if (DocumentsAccess.hasPersistedUri(requireContext())) {
                    "Controleer aliasmapping.csv"
                } else {
                    "Koppel Documents-map"
                }
                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG)
                    .setAction(actionLabel) {
                        if (!DocumentsAccess.hasPersistedUri(requireContext())) {
                            launchPickTree()
                        }
                    }
                    .show()
            }
        }
    }

    /** Start ACTION_OPEN_DOCUMENT_TREE zodat we VoiceTally4 (of Documents) kunnen koppelen. */
    private fun launchPickTree() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, null as Uri?)
        }
        pickTreeLauncher.launch(intent)
    }

    /** Huidige selectie als (canonicals, displayMap). */
    private fun buildSelectionAndDisplayMap(): Pair<List<String>, Map<String, String>> {
        val state = viewModel.uiState.value
        if (state !is UiState.Success) return emptyList<String>() to emptyMap()
        val selected = state.items.filter { viewModel.isSelected(it.tileName) }
        val chosenCanonical = selected.map { it.canonical }
        val displayMap = selected.associate { it.canonical to it.tileName }
        return chosenCanonical to displayMap
    }

    private fun showSelectionHint() {
        val (chosenCanonical, _) = buildSelectionAndDisplayMap()
        val msg = if (chosenCanonical.isEmpty()) {
            getString(R.string.chosen_species_none)
        } else {
            getString(R.string.chosen_species_prefix, chosenCanonical.joinToString(", "))
        }
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
