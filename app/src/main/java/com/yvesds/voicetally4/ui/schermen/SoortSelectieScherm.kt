package com.yvesds.voicetally4.ui.schermen

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
import androidx.navigation.fragment.findNavController
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.yvesds.voicetally4.R
import com.yvesds.voicetally4.databinding.FragmentSoortSelectieSchermBinding
import com.yvesds.voicetally4.ui.adapters.AliasTileAdapter
import com.yvesds.voicetally4.ui.domein.SoortSelectieViewModel
import com.yvesds.voicetally4.ui.domein.SoortSelectieViewModel.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SoortSelectieScherm : Fragment() {

    private var _binding: FragmentSoortSelectieSchermBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SoortSelectieViewModel by viewModels()

    private lateinit var adapter: AliasTileAdapter
    private lateinit var progress: CircularProgressIndicator

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
            isSelected = { tileName -> viewModel.isSelected(tileName) },
            onToggle = { tileName ->
                viewModel.toggleSelection(tileName) // alleen visueel toggelen; items blijven gelijk
                binding.recycler.post { adapter.notifyDataSetChanged() }
                showSelectionSnackbar()
            }
        )
        binding.recycler.adapter = adapter

        // Progress overlay (programmatic, geen XML-wijziging)
        progress = CircularProgressIndicator(requireContext()).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        binding.root.addView(
            progress,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // Center progress telkens als de layout verandert (ook bij rotatie)
        val centerProgress: () -> Unit = {
            progress.x = (binding.root.width - progress.width) / 2f
            progress.y = (binding.root.height - progress.height) / 2f
        }
        binding.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> centerProgress() }
        progress.post { centerProgress() }

        // Observe state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { renderState(it) } }
            }
        }

        // Start laden (eenmalig)
        viewModel.loadAliases()

        // Navigeren naar TallyScherm
        binding.btnOk.setOnClickListener {
            val current = (viewModel.uiState.value as? UiState.Success)?.items.orEmpty()
            val chosenCanonical = current.asSequence()
                .filter { viewModel.isSelected(it.tileName) }
                .map { it.canonical }
                .toList()

            val msg = if (chosenCanonical.isEmpty()) {
                getString(R.string.chosen_species_none)
            } else {
                getString(R.string.chosen_species_prefix, chosenCanonical.joinToString(", "))
            }
            Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()

            // Ga door naar het TallyScherm (action staat in nav_graph onder het bron-fragment)
            findNavController().navigate(R.id.action_soortSelectieScherm_to_tallyScherm)
        }
    }

    private fun renderState(state: UiState) {
        when (state) {
            is UiState.Loading -> {
                binding.emptyView.isVisible = false
                progress.visibility = View.VISIBLE
                progress.contentDescription = state.message
            }
            is UiState.Success -> {
                progress.visibility = View.GONE
                adapter.submitList(state.items)
                binding.emptyView.isVisible = state.items.isEmpty()
            }
            is UiState.Error -> {
                progress.visibility = View.GONE
                adapter.submitList(emptyList())
                binding.emptyView.isVisible = true
                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun showSelectionSnackbar() {
        val current = (viewModel.uiState.value as? UiState.Success)?.items.orEmpty()
        if (current.isEmpty()) return

        val canonical = current.asSequence()
            .filter { viewModel.isSelected(it.tileName) }
            .map { it.canonical }
            .toList()

        val msg = if (canonical.isEmpty()) {
            getString(R.string.chosen_species_none)
        } else {
            getString(R.string.chosen_species_prefix, canonical.joinToString(", "))
        }
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
