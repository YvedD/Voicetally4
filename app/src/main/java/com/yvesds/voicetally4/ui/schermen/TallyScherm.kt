package com.yvesds.voicetally4.ui.schermen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.yvesds.voicetally4.HardwareKeyHandler
import com.yvesds.voicetally4.R
import com.yvesds.voicetally4.databinding.FragmentTallySchermBinding
import com.yvesds.voicetally4.ui.adapters.TallyAdapter
import com.yvesds.voicetally4.ui.adapters.TallyItem
import com.yvesds.voicetally4.ui.tally.SimpleRecognitionListener
import com.yvesds.voicetally4.ui.tally.SpeechParser
import com.yvesds.voicetally4.ui.tally.TallyViewModel
import com.yvesds.voicetally4.utils.settings.SettingsKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

class TallyScherm : Fragment(), HardwareKeyHandler, NieuweSoortenDialoogScherm.Listener {

    private var _binding: FragmentTallySchermBinding? = null
    private val binding get() = _binding!!

    private val vm: TallyViewModel by viewModels()
    private lateinit var parser: SpeechParser

    private var speech: SpeechRecognizer? = null
    private var isListening = false

    private lateinit var tallyAdapter: TallyAdapter
    private lateinit var logAdapter: SimpleLogAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTallySchermBinding.inflate(inflater, container, false)
        parser = SpeechParser(requireContext().applicationContext)
        setupLists()
        setupManualInput()
        return binding.root
    }

    private fun setupLists() {
        tallyAdapter = TallyAdapter(
            onIncrement = { id -> vm.book(id, +1) },
            onDecrement = { id -> vm.book(id, -1) },
            onReset = { id -> vm.reset(id) }
        )
        binding.recyclerTally.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerTally.adapter = tallyAdapter

        logAdapter = SimpleLogAdapter()
        binding.recyclerLog.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        binding.recyclerLog.adapter = logAdapter
    }

    private fun setupManualInput() {
        binding.inputManual.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val text = v.text?.toString().orEmpty()
                if (text.isNotBlank()) {
                    lifecycleScope.launch { handleUtterance(text) }
                    v.text = null
                }
                true
            } else false
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            vm.uiState.collectLatest { st ->
                binding.tvListening.isVisible = st.listening
                binding.tvListening.text = getString(R.string.listening_status_listening)

                logAdapter.submitList(st.speechLog)

                val items = st.totals.entries
                    .map { (id, count) -> TallyItem(id, st.speciesNames[id] ?: id, count) }
                    .sortedBy { it.name.lowercase(Locale.getDefault()) }
                tallyAdapter.submitList(items)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopListening()
        _binding = null
    }

    // ---------------- HardwareKeyHandler ----------------

    override fun onHardwareVolumeUp(): Boolean {
        if (!isAdded || _binding == null) return false
        if (isListening) return true // al bezig -> negeren maar consumeren
        startListening()
        return true
    }

    // ---------------- Speech ----------------

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            Snackbar.make(binding.root, "Spraakherkenning niet beschikbaar", Snackbar.LENGTH_LONG).show()
            return
        }
        if (!hasRecordPermission(requireContext())) {
            Snackbar.make(binding.root, "Microfoon-permissie ontbreekt", Snackbar.LENGTH_LONG).show()
            return
        }

        stopListening() // safety
        speech = SpeechRecognizer.createSpeechRecognizer(requireContext())
        speech?.setRecognitionListener(
            SimpleRecognitionListener(
                onReady = {
                    isListening = true
                    vm.setListening(true)
                },
                onFinal = { text: String ->
                    lifecycleScope.launch { handleUtterance(text) }
                },
                onError = { code: Int ->
                    vm.appendSpeechLog("[ERR:$code]")
                },
                onEnd = {
                    isListening = false
                    vm.setListening(false)
                    stopListening()
                }
            )
        )

        val silenceMs = requireContext()
            .getSharedPreferences(SettingsKeys.SPEECH_PREFS, Context.MODE_PRIVATE)
            .getInt(SettingsKeys.KEY_SPEECH_SILENCE_TIMEOUT_MS, SettingsKeys.DEFAULT_SPEECH_SILENCE_TIMEOUT_MS)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs)
        }
        speech?.startListening(intent)
    }

    private fun stopListening() {
        speech?.stopListening()
        speech?.cancel()
        speech?.destroy()
        speech = null
        isListening = false
        vm.setListening(false)
    }

    private fun hasRecordPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    // ---------------- Parse & apply ----------------

    private suspend fun handleUtterance(text: String) {
        withContext(Dispatchers.Main) {
            vm.appendSpeechLog(text)
        }
        val st = vm.uiState.value
        val res = withContext(Dispatchers.Default) {
            parser.parseOneUtterance(text, st.activeSpecies)
        }

        if (res.active.isNotEmpty()) {
            vm.appendEventLine(JSONObject().apply {
                put("type", "book_active")
                put("items", res.active.map { JSONObject().put("id", it.speciesId).put("name", it.displayName).put("amount", it.amount) })
                put("ts", System.currentTimeMillis())
            })
            res.active.forEach { vm.book(it.speciesId, it.amount) }
        }

        if (res.inactive.isNotEmpty() && isAdded && _binding != null) {
            val proposals = res.inactive.map {
                NieuweSoortenDialoogScherm.Proposal(it.speciesId, it.displayName, it.amount)
            }
            NieuweSoortenDialoogScherm.newInstance(proposals)
                .show(parentFragmentManager, "newSpeciesDialog")
        }
    }

    // ---------------- NieuweSoortenDialoogScherm.Listener ----------------

    override fun onNieuweSoortenGekozen(selected: List<NieuweSoortenDialoogScherm.Proposal>) {
        if (selected.isEmpty()) return

        // Log event
        vm.appendEventLine(JSONObject().apply {
            put("type", "add_inactive_species")
            put("items", selected.map { JSONObject().put("id", it.speciesId).put("name", it.displayName).put("amount", it.amount) })
            put("ts", System.currentTimeMillis())
        })

        // Voeg toe aan actieve set + meteen boeken (met aantallen) — gebruikt bestaande ViewModel API
        val triples = selected.map { Triple(it.speciesId, it.displayName, it.amount) }
        vm.acceptNewSpeciesAndBook(triples)

        // Korte feedback zonder extra stringresource
        Snackbar.make(binding.root, getString(R.string.tally_added_new_species, selected.size), Snackbar.LENGTH_SHORT).show()
    }

    // --------------- simpele log-adapter ---------------

    private class SimpleLogAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<LogVH>() {
        private var data: List<String> = emptyList()
        fun submitList(list: List<String>) { data = list; notifyDataSetChanged() }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogVH {
            val tv = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_log_line, parent, false) as TextView
            return LogVH(tv)
        }
        override fun onBindViewHolder(holder: LogVH, position: Int) { holder.bind(data[position]) }
        override fun getItemCount(): Int = data.size
    }
    private class LogVH(private val tv: TextView) : androidx.recyclerview.widget.RecyclerView.ViewHolder(tv) {
        fun bind(s: String) { tv.text = s }
    }
}
