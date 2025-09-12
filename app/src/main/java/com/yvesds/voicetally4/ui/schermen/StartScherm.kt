package com.yvesds.voicetally4.ui.schermen

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.yvesds.voicetally4.R
import com.yvesds.voicetally4.databinding.FragmentOpstartSchermBinding
import com.yvesds.voicetally4.ui.core.SetupManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * StartScherm:
 * - Permissies + SAF-setup gebeuren in EersteSetupScherm.
 * - Bij 'Start telling': tel CSV-regels en navigeer daarna naar SoortSelectieScherm.
 */
@AndroidEntryPoint
class StartScherm : Fragment() {

    private var _binding: FragmentOpstartSchermBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var sharedPrefs: SharedPreferences
    private lateinit var setupManager: SetupManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupManager = SetupManager(requireContext(), sharedPrefs)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOpstartSchermBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnStartTelling.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launchWhenStarted {
                val res = readAliasMappingCount()
                when (res) {
                    is ReadResult.Success -> {
                        Toast.makeText(requireContext(), "${res.count} soorten ingelezen", Toast.LENGTH_LONG).show()
                        findNavController().navigate(R.id.action_opstartScherm_to_soortSelectieScherm)
                    }
                    is ReadResult.Failure -> {
                        Toast.makeText(requireContext(), res.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        binding.btnTellingen.setOnClickListener {
            Toast.makeText(requireContext(), "Tellingen (TODO navigatie)", Toast.LENGTH_SHORT).show()
        }
        binding.btnInstellingen.setOnClickListener {
            Toast.makeText(requireContext(), "Instellingen (TODO navigatie)", Toast.LENGTH_SHORT).show()
        }
        binding.btnAliassen.setOnClickListener {
            Toast.makeText(requireContext(), "Aliassen (TODO navigatie)", Toast.LENGTH_SHORT).show()
        }
        binding.btnAfsluiten.setOnClickListener { requireActivity().finish() }
    }

    private suspend fun readAliasMappingCount(): ReadResult = withContext(Dispatchers.IO) {
        try {
            val treeUri = setupManager.getPersistedTreeUri()
                ?: return@withContext ReadResult.Failure("Documentroot niet ingesteld. Voer eerst de setup uit.")
            if (!setupManager.hasPersistedPermission(treeUri)) {
                return@withContext ReadResult.Failure("Toegang tot Documenten vervallen. Voer de setup opnieuw uit.")
            }
            val docsTree = DocumentFile.fromTreeUri(requireContext(), treeUri)
                ?: return@withContext ReadResult.Failure("Kon de Documenten-map niet openen.")
            val appRoot = docsTree.findFile("VoiceTally4")?.takeIf { it.isDirectory }
                ?: return@withContext ReadResult.Failure("Map 'VoiceTally4' niet gevonden. Voer de setup opnieuw uit.")
            val assetsDir = appRoot.findFile("assets")?.takeIf { it.isDirectory }
                ?: return@withContext ReadResult.Failure("Map 'assets' niet gevonden. Voer de setup opnieuw uit.")

            val aliasFile = assetsDir.listFiles()
                .firstOrNull { it.name?.equals("aliasmapping.csv", ignoreCase = true) == true }
                ?: return@withContext ReadResult.Failure("Bestand 'aliasmapping.csv' niet gevonden in 'assets'.")

            requireContext().contentResolver.openInputStream(aliasFile.uri).use { input ->
                if (input == null) return@withContext ReadResult.Failure("Kon 'aliasmapping.csv' niet openen.")
                input.bufferedReader(Charsets.UTF_8).use { reader ->
                    val allLines = reader.readLines().asSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
                    if (allLines.isEmpty()) return@use ReadResult.Success(0)
                    val first = allLines.first().lowercase()
                    val hasHeader = "alias" in first || "soort" in first || first.startsWith("#")
                    val count = if (hasHeader) allLines.size - 1 else allLines.size
                    return@use ReadResult.Success(count.coerceAtLeast(0))
                }
            }
        } catch (t: Throwable) {
            return@withContext ReadResult.Failure("Fout bij inlezen: ${t.message ?: t::class.java.simpleName}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private sealed class ReadResult {
    data class Success(val count: Int) : ReadResult()
    data class Failure(val message: String) : ReadResult()
}
