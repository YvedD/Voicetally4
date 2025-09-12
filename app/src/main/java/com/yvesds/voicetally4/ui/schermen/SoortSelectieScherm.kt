package com.yvesds.voicetally4.ui.schermen

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.material.snackbar.Snackbar
import com.yvesds.voicetally4.databinding.FragmentSoortSelectieSchermBinding
import com.yvesds.voicetally4.ui.adapters.AliasTileAdapter
import com.yvesds.voicetally4.ui.core.SetupManager
import com.yvesds.voicetally4.ui.data.SoortAlias
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupManager = SetupManager(requireContext(), sharedPrefs)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
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

        // CSV inladen (IO) en supersnel presenteren (ListAdapter diff in background)
        viewLifecycleOwner.lifecycleScope.launch {
            items = loadAliasesCsv()
            adapter.submitList(items)
            binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }

        // OK: toon canonical namen van de selectie (kolom 1)
        binding.btnOk.setOnClickListener {
            val chosenCanonical = items.asSequence()
                .filter { selectedTiles.contains(it.tileName) }
                .map { it.canonical }
                .toList()
            val msg = if (chosenCanonical.isEmpty())
                "gekozen soorten : (geen)"
            else
                "gekozen soorten : " + chosenCanonical.joinToString(", ")
            Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
            // TODO: navigeer naar tally-scherm met chosenCanonical + evt. soortId/tileName
        }
    }

    private suspend fun loadAliasesCsv(): List<SoortAlias> = withContext(Dispatchers.IO) {
        val treeUri = setupManager.getPersistedTreeUri() ?: return@withContext emptyList()
        if (!setupManager.hasPersistedPermission(treeUri)) return@withContext emptyList()
        val docsTree = DocumentFile.fromTreeUri(requireContext(), treeUri) ?: return@withContext emptyList()
        val appRoot = docsTree.findFile("VoiceTally4")?.takeIf { it.isDirectory } ?: return@withContext emptyList()
        val assets = appRoot.findFile("assets")?.takeIf { it.isDirectory } ?: return@withContext emptyList()
        val csv = assets.listFiles().firstOrNull { it.name?.equals("aliasmapping.csv", true) == true }
            ?: return@withContext emptyList()

        requireContext().contentResolver.openInputStream(csv.uri).use { input ->
            if (input == null) return@withContext emptyList()
            input.bufferedReader(StandardCharsets.UTF_8).use { r ->
                val out = ArrayList<SoortAlias>(1024)
                var firstNonEmptySeen = false
                r.forEachLine { raw ->
                    val line = raw.trim()
                    if (line.isEmpty()) return@forEachLine
                    if (!firstNonEmptySeen) {
                        firstNonEmptySeen = true
                        val lower = line.lowercase()
                        // header heuristiek
                        if (lower.startsWith("soortid;") || "tilename" in lower || "canonical" in lower || lower.startsWith("#")) {
                            return@forEachLine
                        }
                    }
                    val cols = line.split(';')
                    if (cols.size >= 3) {
                        val soortId = cols[0].trim()
                        val canonical = cols[1].trim()
                        val tileName = cols[2].trim()
                        if (soortId.isEmpty() || canonical.isEmpty() || tileName.isEmpty()) return@forEachLine

                        // kolom 3..23 → alias1..alias21 (optioneel)
                        val aliases = if (cols.size > 3) {
                            cols.subList(3, minOf(cols.size, 24))
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                        } else emptyList()

                        out.add(SoortAlias(soortId, canonical, tileName, aliases))
                    }
                }
                return@use out
            }
        }
    }

    private fun showSelectedSnackbar() {
        if (items.isEmpty()) return
        val canonicalList = items.asSequence()
            .filter { selectedTiles.contains(it.tileName) }
            .map { it.canonical }
            .toList()
        val msg = if (canonicalList.isEmpty())
            "gekozen soorten : (geen)"
        else
            "gekozen soorten : " + canonicalList.joinToString(", ")
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
