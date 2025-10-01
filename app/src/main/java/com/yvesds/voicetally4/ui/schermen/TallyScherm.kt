package com.yvesds.voicetally4.ui.schermen

import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.yvesds.voicetally4.R
import com.yvesds.voicetally4.databinding.FragmentTallySchermBinding
import com.yvesds.voicetally4.ui.adapters.SpeechLogAdapter
import com.yvesds.voicetally4.ui.adapters.TallyAdapter
import com.yvesds.voicetally4.ui.adapters.TallyItem
import com.yvesds.voicetally4.ui.shared.SharedSpeciesViewModel
import com.yvesds.voicetally4.ui.tally.TallyViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Tellers als tiles:
 * - tik op tile => open detail-scherm voor manuele invoer/correctie.
 */
class TallyScherm : Fragment() {

    private var _binding: FragmentTallySchermBinding? = null
    private val binding get() = _binding!!

    private val sharedVm: SharedSpeciesViewModel by activityViewModels()
    private val tallyVm: TallyViewModel by viewModels()

    private lateinit var tallyAdapter: TallyAdapter
    private lateinit var logAdapter: SpeechLogAdapter

    private var gridLayoutManager: GridLayoutManager? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTallySchermBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Log-venster
        logAdapter = SpeechLogAdapter()
        binding.recyclerViewSpeechLog.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = logAdapter
            setHasFixedSize(false)
        }
        logAdapter.setAll(listOf("ℹ️ Klaar voor telling…"))

        // Grid voor tiles
        val spanCount = calculateAutoSpanCount(
            resources.displayMetrics,
            binding.tallyRoot.paddingStart + binding.tallyRoot.paddingEnd,
            minTileWidthDp = 120f
        )
        gridLayoutManager = GridLayoutManager(requireContext(), spanCount).also {
            binding.recyclerViewTally.layoutManager = it
        }
        binding.recyclerViewTally.setHasFixedSize(true)
        binding.recyclerViewTally.itemAnimator = null
        binding.recyclerViewTally.setItemViewCacheSize(128)
        binding.recyclerViewTally.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, orr, ob ->
            val wChanged = (r - l) != (orr - ol)
            val hChanged = (b - t) != (ob - ot)
            if (wChanged || hChanged) recalcSpanCount()
        }
        binding.recyclerViewTally.doOnLayout { recalcSpanCount() }

        tallyAdapter = TallyAdapter(
            onTileClick = { speciesId: String, displayName: String, currentCount: String ->
                val args = bundleOf(
                    "mode" to "create",
                    "soortid" to speciesId,
                    "canonicalName" to displayName, // voor nu gebruiken we display als titel
                    "currentCount" to currentCount,
                    "_id" to "" // leeg in create
                )
                findNavController().navigate(
                    R.id.action_tallyScherm_to_waarnemingDetailScherm,
                    args
                )
            }
        )
        binding.recyclerViewTally.adapter = tallyAdapter

        // Shared selectie → Tally VM
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

        // Items naar adapter
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    tallyVm.items.collectLatest { list: List<TallyItem> ->
                        tallyAdapter.submitList(list)
                    }
                }
            }
        }

        // Knoppen
        binding.buttonEndSession.setOnClickListener {
            logAdapter.add(" Save: nog te koppelen aan export/resultaten.")
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

    private fun calculateAutoSpanCount(
        dm: DisplayMetrics,
        rootHorizontalPaddingPx: Int,
        minTileWidthDp: Float
    ): Int {
        val minTilePx = (minTileWidthDp * dm.density).toInt().coerceAtLeast(1)
        val screenPx = dm.widthPixels - rootHorizontalPaddingPx
        return max(1, screenPx / minTilePx)
    }

    private fun recalcSpanCount() {
        val glm = gridLayoutManager ?: return
        val newSpan = calculateAutoSpanCount(
            resources.displayMetrics,
            rootHorizontalPaddingPx = binding.tallyRoot.paddingStart + binding.tallyRoot.paddingEnd,
            minTileWidthDp = 120f
        )
        if (glm.spanCount != newSpan) {
            glm.spanCount = newSpan
            binding.recyclerViewTally.requestLayout()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        gridLayoutManager = null
    }
}
