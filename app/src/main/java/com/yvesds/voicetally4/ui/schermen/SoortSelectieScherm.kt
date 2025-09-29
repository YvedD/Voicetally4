package com.yvesds.voicetally4.ui.schermen

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.DisplayMetrics
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
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.yvesds.voicetally4.R
import com.yvesds.voicetally4.databinding.FragmentSoortSelectieSchermBinding
import com.yvesds.voicetally4.ui.adapters.AliasTileAdapter
import com.yvesds.voicetally4.ui.data.SoortAlias
import com.yvesds.voicetally4.ui.domein.SoortSelectieViewModel
import com.yvesds.voicetally4.ui.domein.SoortSelectieViewModel.UiState
import com.yvesds.voicetally4.ui.shared.SharedSpeciesViewModel
import com.yvesds.voicetally4.utils.io.DocumentsAccess
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Soortselectie met snelle grid (tiles):
 * - Tile-tekst = canonical (altijd), selectie gebeurt op canonical.
 * - displayName (tileName uit store) blijft beschikbaar voor Tally.
 * - SAF-koppeling (Documents) blijft identiek, met herladen na toestemming.
 */
class SoortSelectieScherm : Fragment() {

    private var _binding: FragmentSoortSelectieSchermBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SoortSelectieViewModel by viewModels()
    private val sharedVm: SharedSpeciesViewModel by activityViewModels()

    private lateinit var adapter: AliasTileAdapter
    private var gridLayoutManager: GridLayoutManager? = null

    // ACTION_OPEN_DOCUMENT_TREE voor Documents (of VoiceTally4)
    private val pickTreeLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == Activity.RESULT_OK) {
                val uri: Uri? = res.data?.data
                if (uri != null) {
                    // Persistente toestemming opslaan
                    DocumentsAccess.persistTreeUri(requireContext(), uri)
                    Snackbar.make(binding.root, "Documenten-map gekoppeld.", Snackbar.LENGTH_SHORT)
                        .show()
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

        // Grid met auto kolommen (sneller dan Flexbox)
        val spanCount = calculateAutoSpanCount(
            dm = resources.displayMetrics,
            rootHorizontalPaddingPx = binding.root.paddingStart + binding.root.paddingEnd,
            minTileWidthDp = 120f
        )
        gridLayoutManager = GridLayoutManager(requireContext(), spanCount).also {
            binding.recycler.layoutManager = it
        }
        binding.recycler.setHasFixedSize(true)
        binding.recycler.itemAnimator = null
        binding.recycler.setItemViewCacheSize(128)

        // ⚠️ Selectie op CANONICAL
        adapter = AliasTileAdapter(
            isSelected = { canonical -> viewModel.isSelected(canonical) },
            onToggle = { canonical ->
                viewModel.toggleSelection(canonical)
                updateSelectionStatus()
            }
        )
        binding.recycler.adapter = adapter

        // State observeren
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { renderState(it) } }
            }
        }

        // Start load
        viewModel.loadAliases()

        // OK → deel geselecteerde canonicals + displayMap naar Shared VM en ga naar Tally
        binding.btnOk.setOnClickListener {
            val (chosenCanonical, displayMap) = buildSelectionAndDisplayMap()
            sharedVm.setSelectedSpecies(chosenCanonical)
            if (displayMap.isNotEmpty()) sharedVm.setDisplayNames(displayMap)
            findNavController().navigate(R.id.action_soortSelectieScherm_to_tallyScherm)
        }

        // Init statusregel
        updateSelectionStatus()
    }

    private fun renderState(state: UiState) {
        when (state) {
            is UiState.Loading -> {
                binding.emptyView.isVisible = false
                binding.btnOk.isEnabled = false
            }

            is UiState.Success -> {
                // We geven de adapter als "tileName" de CANONICAL, zodat de tegel dit toont
                // en zodat selectie-key = canonical is.
                val tiles: List<SoortAlias> = state.items.map {
                    SoortAlias(
                        soortId = it.soortId,
                        canonical = it.canonical,
                        tileName = it.canonical,   // ← tonen & selecteren op canonical
                        aliases = it.aliases
                    )
                }
                adapter.submitList(tiles)
                binding.emptyView.isVisible = tiles.isEmpty()
                binding.btnOk.isEnabled = true
                updateSelectionStatus()
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

    /**
     * Huidige selectie:
     * - canonicals (lijst) voor Tally
     * - displayMap: canonical -> display tileName (zoals uit de store)
     *
     * Let op: uiState houdt nog de *oorspronkelijke* records bij waar
     * it.tileName = displayName. We filteren met isSelected(it.canonical).
     */
    private fun buildSelectionAndDisplayMap(): Pair<List<String>, Map<String, String>> {
        val state = viewModel.uiState.value
        if (state !is UiState.Success) return emptyList<String>() to emptyMap()

        val selected = state.items.filter { viewModel.isSelected(it.canonical) }

        val chosenCanonical = selected.map { it.canonical }
        val displayMap = selected.associate { it.canonical to it.tileName } // display = tileName uit store
        return chosenCanonical to displayMap
    }

    private fun updateSelectionStatus() {
        val (chosenCanonical, _) = buildSelectionAndDisplayMap()
        val msg = if (chosenCanonical.isEmpty()) {
            getString(R.string.chosen_species_none)
        } else {
            getString(R.string.chosen_species_prefix, chosenCanonical.joinToString(", "))
        }
        binding.selectionStatus.text = msg
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        gridLayoutManager = null
    }

    private fun calculateAutoSpanCount(
        dm: DisplayMetrics,
        rootHorizontalPaddingPx: Int,
        minTileWidthDp: Float
    ): Int {
        val minTilePx = (minTileWidthDp * dm.density).toInt().coerceAtLeast(1)
        val screenPx = dm.widthPixels - rootHorizontalPaddingPx
        val span = max(1, screenPx / minTilePx)
        return span
    }
}
