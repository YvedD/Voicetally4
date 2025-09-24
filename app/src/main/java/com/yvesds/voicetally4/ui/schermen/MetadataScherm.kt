package com.yvesds.voicetally4.ui.schermen

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.TextView
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.yvesds.voicetally4.R
import com.yvesds.voicetally4.databinding.FragmentMetadataSchermBinding
import com.yvesds.voicetally4.ui.data.MetadataForm
import com.yvesds.voicetally4.utils.net.TrektellenApi
import com.yvesds.voicetally4.utils.upload.CountsPayloadBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MetadataScherm : Fragment() {

    private var _binding: FragmentMetadataSchermBinding? = null
    private val binding get() = _binding!!

    private val locale = Locale.getDefault()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", locale)
    private val timeFmt = SimpleDateFormat("HH:mm", locale)

    // UI-kleuren voor de tijd-dialog (zoals gevraagd)
    private val accentBlue = Color.parseColor("#08C7E4")
    private val darkBg = Color.parseColor("#121212")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMetadataSchermBinding.inflate(inflater, container, false)
        return binding.rootCard
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupStaticUi()
        setupDatePicker()
        setupTimeSpinnerDialog()
        setupDropdowns()
        setupButtons()
        prefillDefaults()
    }

    // -------------------------
    // Setup helpers
    // -------------------------
    private fun setupStaticUi() {
        // voorkom keyboard
        binding.etDatum.inputType = InputType.TYPE_NULL
        binding.etTijd.inputType = InputType.TYPE_NULL

        // defaults voor datum/tijd
        binding.etDatum.setText(dateFmt.format(Date()))
        binding.etTijd.setText(timeFmt.format(Date()))
    }

    private fun setupDatePicker() {
        binding.etDatum.setOnClickListener {
            val cal = Calendar.getInstance()
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH)
            val d = cal.get(Calendar.DAY_OF_MONTH)

            val dlg = DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                binding.etDatum.setText(dateFmt.format(Date(picked.timeInMillis)))
            }, y, m, d)

            dlg.show()
        }
    }

    private fun setupTimeSpinnerDialog() {
        binding.etTijd.setOnClickListener {
            showTimeSpinner(
                initial = binding.etTijd.text?.toString().takeIf { !it.isNullOrBlank() } ?: timeFmt.format(Date()),
            ) { hhmm ->
                binding.etTijd.setText(hhmm)
            }
        }
    }

    private fun setupDropdowns() {
        // Telposten
        val labels = resources.getStringArray(R.array.vt4_telpost_labels)
        binding.acTelpost.setSimpleItems(labels)
        if (labels.isNotEmpty()) binding.acTelpost.setText(labels[0], false)

        // Windrichting
        if (binding.acWindrichting.text.isNullOrBlank()) {
            binding.acWindrichting.setSimpleItems(resources.getStringArray(R.array.vt4_windrichting_opties))
            binding.acWindrichting.setText("ZW", false)
        }

        // Bewolking (achtsten)
        binding.acBewolking.setSimpleItems(resources.getStringArray(R.array.vt4_bewolking_achtsten))
        if (binding.acBewolking.text.isNullOrBlank()) binding.acBewolking.setText("0/8", false)

        // Neerslag
        binding.acNeerslag.setSimpleItems(resources.getStringArray(R.array.vt4_neerslag_opties))
        if (binding.acNeerslag.text.isNullOrBlank()) binding.acNeerslag.setText("geen", false)

        // Windkracht (Bft)
        binding.acWindkracht.setSimpleItems(resources.getStringArray(R.array.vt4_windkracht_beaufort))
        if (binding.acWindkracht.text.isNullOrBlank()) binding.acWindkracht.setText("3bf", false)

        // Type telling
        binding.acTypeTelling.setSimpleItems(resources.getStringArray(R.array.vt4_type_telling_opties))
        if (binding.acTypeTelling.text.isNullOrBlank()) binding.acTypeTelling.setText("alle soorten", false)
    }

    private fun setupButtons() {
        binding.btnAnnuleer.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnVerder.setOnClickListener {
            val form = collectFormOrNull() ?: return@setOnClickListener
            lifecycleScope.launch {
                // combineer datum + tijd naar epoch seconden
                val beginSec = withContext(Dispatchers.Default) {
                    combineDateTimeToEpochSeconds(form.datumEpochMillis, form.timeHHmm)
                }
                val eindSec = beginSec // bij start gelijk aan begin

                val payloadJson = withContext(Dispatchers.Default) {
                    CountsPayloadBuilder.buildStart(
                        form = form,
                        beginSec = beginSec,
                        eindSec = eindSec,
                        weerOpmerking = form.opmerkingen, // vrije opmerking “Weer :”
                        telpostId = form.telpostCode
                    )
                }

                // netwerk off-main
                val resp = TrektellenApi.postCountsSaveBasicAuthAsync(requireContext(), payloadJson)
                if (resp.ok) {
                    parseOkMessageAndId(resp.body)?.let { (message, onlineId) ->
                        if (message == "OK") {
                            Snackbar.make(
                                requireView(),
                                "Telling gestart = OK, ID: $onlineId",
                                Snackbar.LENGTH_LONG
                            ).show()
                            navigateToSoortSelectie()
                        }
                    }
                }
                // volgens afspraak: geen extra toasts/snackbars bij fouten hier
            }
        }

        // Weer automatisch ophalen (klaar voor asynchrone invulling)
        binding.btnWeerIcon.setOnClickListener {
            lifecycleScope.launch {
                fillWeatherFast()
            }
        }
    }

    private fun prefillDefaults() {
        // (optioneel: laatste waarden uit prefs zetten)
    }

    // -------------------------
    // Form helpers
    // -------------------------
    private fun collectFormOrNull(): MetadataForm? {
        val dateStr = binding.etDatum.text?.toString()?.trim().orEmpty()
        val timeStr = binding.etTijd.text?.toString()?.trim().orEmpty()
        if (dateStr.isBlank() || timeStr.isBlank()) return null

        val dateMillis = runCatching { dateFmt.parse(dateStr)!!.time }.getOrNull() ?: return null

        val telpostLabel = binding.acTelpost.text?.toString()?.trim().orEmpty()
        val telpostCode = resolveTelpostCodeFromLabel(telpostLabel)

        val tellersNaam = binding.etTellers.text?.toString()?.trim().orEmpty()
        val tellersId = "" // (optioneel) in te vullen wanneer UI/bron aanwezig is

        val windrichting = binding.acWindrichting.text?.toString()?.trim().orEmpty()
        val temperatuurC = binding.etTemperatuur.text?.toString()?.toDoubleOrNull()
        val bewolking = binding.acBewolking.text?.toString()?.trim().orEmpty()
        val neerslag = binding.acNeerslag.text?.toString()?.trim().orEmpty()
        val zichtKm = binding.etZicht.text?.toString()?.toDoubleOrNull()?.let { it / 1000.0 } // meters -> km
        val windkracht = binding.acWindkracht.text?.toString()?.trim().orEmpty()
        val luchtdrukHpa = binding.etLuchtdruk.text?.toString()?.toDoubleOrNull()

        val opmerkingenVrij = binding.etWeerOpmerking.text?.toString()?.trim().orEmpty()
        val typeTelling = binding.acTypeTelling.text?.toString()?.trim().orEmpty()

        // locatieType: voor nu geen aparte UI – placeholder consistent met je strings
        val locatieType = getString(R.string.meta_locatie_type)

        return MetadataForm(
            typeTelling = typeTelling,
            locatieType = locatieType,
            datumEpochMillis = dateMillis,
            timeHHmm = timeStr,
            telpostLabel = telpostLabel,
            telpostCode = telpostCode,
            tellersNaam = tellersNaam,
            tellersId = tellersId,
            windrichting = windrichting,
            temperatuurC = temperatuurC,
            bewolkingAchtsten = bewolking,
            neerslag = neerslag,
            zichtKm = zichtKm,
            windkrachtBft = windkracht,
            luchtdrukHpa = luchtdrukHpa,
            opmerkingen = opmerkingenVrij
        )
    }

    private fun resolveTelpostCodeFromLabel(label: String): String {
        val labels = resources.getStringArray(R.array.vt4_telpost_labels)
        val values = resources.getStringArray(R.array.vt4_telpost_values)
        val idx = labels.indexOfFirst { it.equals(label, ignoreCase = true) }
        return values.getOrNull(idx) ?: ""
    }

    private fun combineDateTimeToEpochSeconds(dateMillis: Long, hhmm: String): Long {
        val (h, m) = hhmm.split(":").let {
            (it.getOrNull(0)?.toIntOrNull() ?: 0) to (it.getOrNull(1)?.toIntOrNull() ?: 0)
        }
        val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
        cal.set(Calendar.HOUR_OF_DAY, h)
        cal.set(Calendar.MINUTE, m)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis / 1000L
    }

    // -------------------------
    // Network/IO placeholders
    // -------------------------
    private suspend fun fillWeatherFast() = withContext(Dispatchers.IO) {
        // Hier kan je later WeatherManager aanroepen zodra locatie beschikbaar is.
        // Dit draait reeds off-main (IO).
    }

    private fun navigateToSoortSelectie() {
        val nav = androidx.navigation.fragment.findNavController(this)
        nav.navigate(R.id.action_metadataScherm_to_soortSelectieScherm)
    }

    // -------------------------
    // Response parsing
    // -------------------------
    private fun parseOkMessageAndId(body: String): Pair<String, String>? {
        // Verwacht: {"json":[{"message":"OK","gelukt_array":[{"id":"","onlineid":3169869}], ...}]}
        return runCatching {
            val root = JSONObject(body)
            val arr = root.optJSONArray("json") ?: return null
            val obj = arr.optJSONObject(0) ?: return null
            val msg = obj.optString("message", "")
            val onlineId = obj.optJSONArray("gelukt_array")
                ?.optJSONObject(0)
                ?.opt("onlineid")
                ?.toString()
                ?: ""
            msg to onlineId
        }.getOrNull()
    }

    // -------------------------
    // Tijd-dialoog (donkere bg, lichtblauwe tekst)
    // -------------------------
    private fun showTimeSpinner(initial: String, onPicked: (String) -> Unit) {
        val (initH, initM) = initial.split(":").let {
            (it.getOrNull(0)?.toIntOrNull() ?: 0) to (it.getOrNull(1)?.toIntOrNull() ?: 0)
        }

        // Let op: we gebruiken jouw reeds aanwezige layout-naam:
        // res/layout/time_spinner_dialog.xml
        val content = layoutInflater.inflate(R.layout.time_spinner_dialog, null)
        content.setBackgroundColor(darkBg)

        val npHour = content.findViewById<NumberPicker>(R.id.npHour).apply {
            minValue = 0; maxValue = 23; value = initH
            setFormatter { String.format("%02d", it) }
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }
        val npMin = content.findViewById<NumberPicker>(R.id.npMinute).apply {
            minValue = 0; maxValue = 59; value = initM
            setFormatter { String.format("%02d", it) }
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }

        fun tintPickerText(np: NumberPicker) {
            for (child in np.children) {
                if (child is TextView) child.setTextColor(accentBlue)
            }
            // Divider verwijderen (best effort)
            try {
                val f = NumberPicker::class.java.getDeclaredField("mSelectionDivider")
                f.isAccessible = true
                f.set(np, null)
            } catch (_: Throwable) { /* ignore */ }
        }
        tintPickerText(npHour)
        tintPickerText(npMin)

        val dlg = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.meta_kies_tijd_title))
            .setView(content)
            .setPositiveButton(getString(R.string.ok)) { d, _ ->
                val hhmm = String.format(Locale.getDefault(), "%02d:%02d", npHour.value, npMin.value)
                onPicked(hhmm)
                d.dismiss()
            }
            .setNegativeButton(getString(R.string.annuleer)) { d, _ -> d.dismiss() }
            .create()

        dlg.setOnShowListener {
            dlg.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dlg.show()

        dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(accentBlue)
        dlg.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(accentBlue)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
