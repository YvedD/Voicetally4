package com.yvesds.voicetally4.ui.schermen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.voicetally4.R
import com.yvesds.voicetally4.shared.SharedSpeciesViewModel
import com.yvesds.voicetally4.ui.adapters.SpeciesTileAdapter
import com.yvesds.voicetally4.ui.dialogs.SpeciesAdjustDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TallyScherm : Fragment() {

    private val sharedVm: SharedSpeciesViewModel by activityViewModels()

    private lateinit var recyclerLog: RecyclerView
    private lateinit var recyclerSpecies: RecyclerView
    private lateinit var tvListening: TextView
    private lateinit var inputManual: EditText

    private lateinit var logAdapter: SpeechLogAdapter
    private lateinit var speciesAdapter: SpeciesTileAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_tally_scherm, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerLog = view.findViewById(R.id.recyclerLog)
        recyclerSpecies = view.findViewById(R.id.recyclerSpecies)
        tvListening = view.findViewById(R.id.tvListening)
        inputManual = view.findViewById(R.id.inputManual)

        // Log bovenaan
        logAdapter = SpeechLogAdapter()
        recyclerLog.apply {
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
            adapter = logAdapter
            setHasFixedSize(true)
        }

        // Species tiles (grid) met display-namen uit sharedVm.displayNames
        val span = resources.getInteger(R.integer.vt4_tally_span_count).coerceAtLeast(2)
        speciesAdapter = SpeciesTileAdapter(
            onItemClick = { speciesCanonical ->
                SpeciesAdjustDialogFragment.newInstance(speciesCanonical)
                    .show(childFragmentManager, "SpeciesAdjustDialog")
            },
            displayNameOf = { canonical -> sharedVm.displayNameOf(canonical) }
        )
        recyclerSpecies.apply {
            layoutManager = GridLayoutManager(requireContext(), span)
            adapter = speciesAdapter
            setHasFixedSize(true)
            itemAnimator = null
        }

        // Observe flows
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Logregels
                launch {
                    sharedVm.speechLogs.collect { logs ->
                        logAdapter.submit(logs)
                        recyclerLog.post {
                            recyclerLog.scrollToPosition((logs.size - 1).coerceAtLeast(0))
                        }
                    }
                }
                // Tellers + display-namen.
                // Bij elke wijziging in teller of naam-map, lijst opnieuw opbouwen.
                launch {
                    sharedVm.tallyMap.collect { map ->
                        val items = sharedVm.selectedSpecies.value.map { s ->
                            SpeciesTileAdapter.Tile(s, map[s] ?: 0)
                        }
                        speciesAdapter.submit(items)
                    }
                }
                launch {
                    sharedVm.displayNames.collect {
                        // display-namen gewijzigd → zelfde canonieke set opnieuw renderen
                        val map = sharedVm.tallyMap.value
                        val items = sharedVm.selectedSpecies.value.map { s ->
                            SpeciesTileAdapter.Tile(s, map[s] ?: 0)
                        }
                        speciesAdapter.submit(items)
                    }
                }
            }
        }

        // Handmatige invoer (debug) → naar log
        inputManual.setOnEditorActionListener { v, _, _ ->
            val text = v.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                sharedVm.appendLog("MANUAL: $text")
                v.text = null
            }
            true
        }

        // Listening-label optioneel zichtbaar maken zodra je spraak koppelt
        tvListening.isVisible = false
    }

    // --- Simpele log adapter (gebaseerd op item_log_line.xml) ---
    private class SpeechLogAdapter : RecyclerView.Adapter<SpeechLogAdapter.VH>() {
        private val items = ArrayList<String>()

        fun submit(list: List<String>) {
            val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = items.size
                override fun getNewListSize() = list.size
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                    oldItemPosition == newItemPosition
                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                    items[oldItemPosition] == list[newItemPosition]
            })
            items.clear()
            items.addAll(list)
            diff.dispatchUpdatesTo(this)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_log_line, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.tv.text = items[position]
        }

        override fun getItemCount() = items.size

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tv: TextView = view.findViewById(R.id.tvLogLine)
        }
    }
}
