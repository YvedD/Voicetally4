package com.yvesds.voicetally4.ui.schermen

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.material.snackbar.Snackbar
import com.yvesds.voicetally4.R
import com.yvesds.voicetally4.databinding.FragmentSoortSelectieSchermBinding
import com.yvesds.voicetally4.ui.adapters.AliasTileAdapter
import com.yvesds.voicetally4.ui.core.SetupManager
import com.yvesds.voicetally4.ui.data.SoortAlias
import com.yvesds.voicetally4.ui.domein.SoortSelectieViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SoortSelectieScherm : Fragment() {

    private var _binding: FragmentSoortSelectieSchermBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var sharedPrefs: SharedPreferences

    private lateinit var setupManager: SetupManager
    private lateinit var adapter: AliasTileAdapter

    private var items: List<SoortAlias> = emptyList()

    /** Geselecteerde keys = tileName (kolom 2). */
    private val selectedTiles = linkedSetOf<String>() // linked -> behoud volgorde

    private val viewModel: SoortSelectieViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupManager = SetupManager(requireContext(), sharedPrefs)
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

        // Flexbox: wrap tiles, zoveel mogelijk per rij
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
            isSelected = { tileName -> selectedTiles.contains(tileName) },
            onToggle = { tileName ->
                if (!selectedTiles.add(tileName)) selectedTiles.remove(tileName)
                showSelectedSnackbar()
            }
        )
        binding.recycler.adapter = adapter

        // Observeer state: Loading → Data/Error
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is SoortSelectieViewModel.UiState.Idle -> {
                            showLoadingUi(isLoading = true, message = getString(R.string.loading_species))
                        }
                        is SoortSelectieViewModel.UiState.Loading -> {
                            showLoadingUi(isLoading = true, message = getString(R.string.loading_species))
                        }
                        is SoortSelectieViewModel.UiState.Data -> {
                            items = state.items
                            adapter.submitList(items)
                            showLoadingUi(isLoading = false)
                            binding.emptyView.isVisible = items.isEmpty()
                        }
                        is SoortSelectieViewModel.UiState.Error -> {
                            showLoadingUi(isLoading = false)
                            binding.emptyView.isVisible = true
                            // Toon foutmelding op emptyView indien TextView, anders via snackbar
                            val v = binding.emptyView
                            val text = state.message.ifBlank { getString(R.string.loading_failed_generic) }
                            when (v) {
                                is android.widget.TextView -> v.text = text
                                else -> Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }

        // Start lazy load
        viewModel.loadAliases(requireContext(), setupManager)

        // OK: toon canonical namen van de selectie (kolom 1)
        binding.btnOk.setOnClickListener {
            val chosenCanonical = items.asSequence()
                .filter { selectedTiles.contains(it.tileName) }
                .map { it.canonical }
                .toList()

            val msg = if (chosenCanonical.isEmpty()) {
                getString(R.string.chosen_species_none)
            } else {
                getString(R.string.chosen_species_prefix, chosenCanonical.joinToString(", "))
            }
            Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()

            // TODO: navigeer naar tally-scherm met chosenCanonical + evt. soortId/tileName
        }
    }

    private fun showLoadingUi(isLoading: Boolean, message: String? = null) {
        binding.recycler.isVisible = !isLoading
        binding.emptyView.isVisible = isLoading
        message?.let { msg ->
            val v = binding.emptyView
            if (v is android.widget.TextView) v.text = msg
        }
    }

    private fun showSelectedSnackbar() {
        if (items.isEmpty()) return
        val canonicalList = items.asSequence()
            .filter { selectedTiles.contains(it.tileName) }
            .map { it.canonical }
            .toList()
        val msg = if (canonicalList.isEmpty()) {
            getString(R.string.chosen_species_none)
        } else {
            getString(R.string.chosen_species_prefix, canonicalList.joinToString(", "))
        }
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
