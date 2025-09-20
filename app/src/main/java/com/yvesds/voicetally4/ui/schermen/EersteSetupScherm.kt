package com.yvesds.voicetally4.ui.schermen

import android.Manifest
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yvesds.voicetally4.R
import com.yvesds.voicetally4.databinding.FragmentEersteSetupSchermBinding
import com.yvesds.voicetally4.ui.core.SetupManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class EersteSetupScherm : Fragment() {

    private var _binding: FragmentEersteSetupSchermBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var sharedPrefs: SharedPreferences

    private lateinit var setup: SetupManager
    private var flowStarted = false

    private val allPermissions: Array<String> by lazy {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    private val multiPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filterValues { !it }.keys
        if (denied.isEmpty()) {
            continueSetupAfterPermissions()
        } else {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.perm_denied_title)
                .setMessage(getString(R.string.perm_denied_msg, denied.joinToString()))
                .setPositiveButton(R.string.try_again) { _, _ -> startSetupFlow() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> setBusy(false) }
                .show()
        }
    }

    private val treePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val uri: Uri? = res.data?.data
        if (uri == null) {
            Toast.makeText(requireContext(), getString(R.string.setup_saf_cancelled), Toast.LENGTH_LONG).show()
            setBusy(false)
            return@registerForActivityResult
        }

        // Bewaar & persisteer permissie met de effectief teruggegeven flags
        val grantedFlags = res.data?.flags ?: 0
        setup.savePersistedTreeUri(uri)
        setup.persistUriPermission(uri, grantedFlags)

        // Maak/controleer mapstructuur
        val r = setup.ensureFolderStructure(uri)
        if (r.isSuccess) {
            // Zet vlag zodat Activity bij volgende keer meteen het opstartscherm kiest
            setup.markSetupDone()
            Toast.makeText(requireContext(), getString(R.string.setup_done), Toast.LENGTH_SHORT).show()
            navigateToStart()
        } else {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.setup_error_title)
                .setMessage(r.exceptionOrNull()?.message ?: getString(R.string.setup_error_fallback))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            setBusy(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setup = SetupManager(requireContext(), sharedPrefs)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentEersteSetupSchermBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Als alles al klaar is en je belandt hier toch (bv. na Activity recreate), navigeer veilig weg.
        if (setup.isSetupComplete() || setup.isSetupDoneFlag()) {
            navigateToStart()
            return
        }

        if (!flowStarted) {
            flowStarted = true
            startSetupFlow()
        }
    }

    private fun startSetupFlow() {
        setBusy(true)
        if (hasAllPermissions()) {
            continueSetupAfterPermissions()
        } else {
            multiPermLauncher.launch(allPermissions)
        }
    }

    private fun continueSetupAfterPermissions() {
        // Hebben we al een persistente Documents-URI? Dan enkel structuur checken.
        val existing = setup.getPersistedTreeUri()
        if (existing != null && setup.hasPersistedPermission(existing)) {
            val r = setup.ensureFolderStructure(existing)
            if (r.isSuccess) {
                setup.markSetupDone()
                navigateToStart()
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.setup_error_title)
                    .setMessage(r.exceptionOrNull()?.message ?: getString(R.string.setup_error_fallback))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                setBusy(false)
            }
            return
        }

        // Geen persistente root → vraag via SAF, start in /Documents indien mogelijk
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, setup.buildInitialDocumentsUri())
        }
        treePickerLauncher.launch(intent)
    }

    private fun hasAllPermissions(): Boolean =
        allPermissions.all { perm ->
            ContextCompat.checkSelfPermission(requireContext(), perm) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    /**
     * **Belangrijk**: Navigeer **zonder** action-ID, en pop de setup uit de stack.
     * Dit voorkomt de crash “action … cannot be found from current destination …”
     * wanneer de Activity de startDestination al op `opstartScherm` heeft gezet.
     */
// In com.yvesds.voicetally4.ui.schermen.EersteSetupScherm

    private fun navigateToStart() {
        if (!isAdded) return
        val nav = findNavController()

        // Forceer naar opstartScherm, ook als currentDestination al opstartScherm is.
        val options = androidx.navigation.navOptions {
            // Verwijder setup uit de backstack
            popUpTo(R.id.eersteSetupScherm) { inclusive = true }
            // Geen anim-flits
            anim {
                enter = 0; exit = 0; popEnter = 0; popExit = 0
            }
            launchSingleTop = true
        }

        // Als je daadwerkelijk op EersteSetup staat, mag je de action gebruiken;
        // anders rechtstreeks naar destination id (voorkomt "action not found from current dest").
        if (nav.currentDestination?.id == R.id.eersteSetupScherm) {
            nav.navigate(R.id.action_eersteSetupScherm_to_opstartScherm, null, options)
        } else {
            nav.navigate(R.id.opstartScherm, null, options)
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.progress.isVisible = busy
        binding.tvBusy.isVisible = busy
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
