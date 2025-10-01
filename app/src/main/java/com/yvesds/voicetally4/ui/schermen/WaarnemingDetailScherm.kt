package com.yvesds.voicetally4.ui.schermen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yvesds.voicetally4.databinding.FragmentWaarnemingDetailSchermBinding
import com.yvesds.voicetally4.ui.data.ObservationRecord
import com.yvesds.voicetally4.ui.tally.TallyViewModel
import com.yvesds.voicetally4.utils.codes.CodesRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar
import kotlin.math.max

/**
 * WaarnemingDetailScherm
 * - gebruikt jouw aangepaste layout
 * - knoppen (leeftijd/geslacht/kleed/locatie) zijn checkable en lichten op bij selectie
 * - Save: bouwt ObservationRecord (alle velden String), mapt labels via codes.json,
 *         slaat op in TallyViewModel, toont toast + tijdelijke JSON-voorvertoning
 */
class WaarnemingDetailScherm : Fragment() {

    private var _binding: FragmentWaarnemingDetailSchermBinding? = null
    private val binding get() = _binding!!

    private val tallyVm: TallyViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWaarnemingDetailSchermBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Zorg dat codes geladen zijn (NL labels → servercodes)
        CodesRepository.ensureLoaded(requireContext().applicationContext)

        // Args uit Bundle (geen SafeArgs)
        val soortid = arguments?.getString("soortid").orEmpty()
        val canonicalName = arguments?.getString("canonicalName").orEmpty()
        val currentCount = arguments?.getString("currentCount").orEmpty()
        val idInternal = arguments?.getString("_id").orEmpty()

        // Header + tijd
        binding.tvCanonicalName.text = canonicalName
        setupTimeSpinners()

        // Aantellerlabels volgens seizoen
        val mainDir = computeSeasonalMainDirection() // "NO" of "ZW"
        val otherDir = if (mainDir == "NO") "ZW" else "NO"
        binding.lblAantal.text = mainDir
        binding.lblAantalTerug.text = otherDir

        // Aantellers (+/-)
        setupCounters()

        // Attribuut-knoppen checkable + exclusieve selectie per kolom
        setupAttributeButtons()

        // startwaarde voor create: huidige tegelcount
        binding.etAantal.setText(currentCount.ifBlank { "0" })

        // Acties
        binding.btnSaveRecord.setOnClickListener {
            saveRecord(soortid, idInternal, mainDir)
        }
        binding.btnCancel.setOnClickListener { findNavController().popBackStack() }
        binding.btnDeleteRecord.setOnClickListener {
            Toast.makeText(requireContext(), "Delete volgt in de volgende stap", Toast.LENGTH_SHORT).show()
        }
    }

    // ----- Save -----

    private fun saveRecord(soortid: String, existingId: String, mainDir: String) {
        // tijd uit spinners
        val hour = (binding.spHour.selectedItem as? String)?.toIntOrNull() ?: 0
        val minute = (binding.spMinute.selectedItem as? String)?.toIntOrNull() ?: 0
        val epoch = tallyVm.epochSecondsForToday(hour, minute)
        val uploadTs = tallyVm.nowFormatted()

        // aantallen
        val aantal = binding.etAantal.text?.toString()?.trim().ifNullOrBlank("0")
        val aantalTerug = binding.etAantalTerug.text?.toString()?.trim().ifNullOrBlank("0")
        val lokaal = binding.etLokaal.text?.toString()?.trim().ifNullOrBlank("0")

        // markering
        val markeerTrek = if (binding.cbMarkeerTrek.isChecked) "1" else "0"
        val markeerLokaal = if (binding.cbMarkeerLokaal.isChecked) "1" else "0"

        // labels → servercodes via CodesRepository (fallbacks indien nodig)
        val geslachtLabel = selectedLabel(binding.btnGeslachtMan, binding.btnGeslachtVrouw, binding.btnGeslachtOnbekend)
        val leeftijdLabel = selectedLabel(
            binding.btnLeeftijdAdult, binding.btnLeeftijdJuv, binding.btnLeeftijdGt1kj,
            binding.btnLeeftijd1kj, binding.btnLeeftijd2kj, binding.btnLeeftijd3kj, binding.btnLeeftijd4kj
        )
        val kleedLabel = selectedLabel(
            binding.btnKleedZomer, binding.btnKleedWinter, binding.btnKleedIntermediair,
            binding.btnKleedLicht, binding.btnKleedDonker, binding.btnKleedEclips
        )
        val locatieLabel = selectedLabel(
            binding.btnLocZee, binding.btnLocBranding, binding.btnLocDuinen,
            binding.btnLocCampings, binding.btnLocPolders
        )

        val geslachtCode = CodesRepository.labelToCode("geslacht", geslachtLabel) ?: fallbackGeslacht(geslachtLabel)
        val leeftijdCode = CodesRepository.labelToCode("leeftijd", leeftijdLabel) ?: fallbackLeeftijd(leeftijdLabel)
        val kleedCode = CodesRepository.labelToCode("kleed", kleedLabel) ?: fallbackKleed(kleedLabel)
        val locatieCode = CodesRepository.labelToCode("locatie", locatieLabel) ?: ""

        val total = safeSum(aantal, aantalTerug, lokaal).toString()

        val rec = ObservationRecord(
            _id = existingId,            // VM geeft oplopend id als leeg
            tellingid = "",              // VM zet tellingid
            soortid = soortid,
            aantal = aantal,
            richting = "",               // (nog niet in UI)
            aantalterug = aantalTerug,
            richtingterug = "",          // (nog niet in UI)
            sightingdirection = mainDir,
            lokaal = lokaal,
            aantal_plus = "0",
            aantalterug_plus = "0",
            lokaal_plus = "0",
            markeren = markeerTrek,
            markerenlokaal = markeerLokaal,
            geslacht = geslachtCode,
            leeftijd = leeftijdCode,
            kleed = kleedCode,
            opmerkingen = "",
            trektype = "",
            teltype = "",
            location = locatieCode,
            height = "",
            tijdstip = epoch,
            groupid = "",
            uploadtijdstip = uploadTs,
            totaalaantal = total
        )

        tallyVm.addRecord(rec)

        Toast.makeText(requireContext(), "record opgeslagen!", Toast.LENGTH_SHORT).show()

        // Tijdelijke JSON-voorvertoning (kan later verwijderd worden)
        showRecordPreview(rec)

        findNavController().popBackStack()
    }

    private fun showRecordPreview(rec: ObservationRecord) {
        val jsonPretty = Json { prettyPrint = true }.encodeToString(rec)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Record opgeslagen")
            .setMessage(jsonPretty)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun String?.ifNullOrBlank(fallback: String) =
        if (this.isNullOrBlank()) fallback else this

    private fun safeSum(vararg nums: String): Int =
        nums.mapNotNull { it.toIntOrNull() }.sum()

    private fun selectedLabel(vararg buttons: MaterialButton): String {
        buttons.forEach { if (it.isChecked) return it.text?.toString() ?: "" }
        return ""
    }

    // --- Fallback codes (alleen als codes.json geen match levert) ---
    private fun fallbackGeslacht(label: String): String = when (label.lowercase()) {
        "man" -> "M"; "vrouw" -> "V"; "onbek.", "onbekend", "?" -> "?"
        else -> ""
    }
    private fun fallbackLeeftijd(label: String): String = when (label.lowercase()) {
        "adult" -> "A"; "juveniel" -> "J"; ">1kj" -> ">1KJ"; "1kj" -> "1KJ";
        "2kj" -> "2KJ"; "3kj" -> "3KJ"; "4kj" -> "4KJ";
        else -> ""
    }
    private fun fallbackKleed(label: String): String = when (label.lowercase()) {
        "zomer", "zomerkleed" -> "Z"
        "winter", "winterkleed" -> "W"
        "interm.", "intermediair" -> "I"
        "licht" -> "L"
        "donker" -> "D"
        "eclips" -> "E"
        else -> ""
    }

    // ----- UI helpers -----

    private fun setupTimeSpinners() {
        val hours = (0..23).map { it.toString().padStart(2, '0') }
        val minutes = (0..59).map { it.toString().padStart(2, '0') }

        binding.spHour.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, hours
        )
        binding.spMinute.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, minutes
        )

        val cal = Calendar.getInstance()
        binding.spHour.setSelection(cal.get(Calendar.HOUR_OF_DAY))
        binding.spMinute.setSelection(cal.get(Calendar.MINUTE))
    }

    /** Jan–Jun -> NO, Jul–Dec -> ZW */
    private fun computeSeasonalMainDirection(): String {
        val month = Calendar.getInstance().get(Calendar.MONTH) // 0..11
        return if (month in 0..5) "NO" else "ZW"
    }

    private fun setupCounters() = with(binding) {
        fun parseInt(s: String?): Int = s?.toIntOrNull() ?: 0
        fun bump(et: com.google.android.material.textfield.TextInputEditText, delta: Int) {
            val v = parseInt(et.text?.toString()) + delta
            et.setText(max(0, v).toString())
        }
        btnAantalPlus.setOnClickListener { bump(etAantal, +1) }
        btnAantalMin.setOnClickListener { bump(etAantal, -1) }
        btnAantalTerugPlus.setOnClickListener { bump(etAantalTerug, +1) }
        btnAantalTerugMin.setOnClickListener { bump(etAantalTerug, -1) }
        btnLokaalPlus.setOnClickListener { bump(etLokaal, +1) }
        btnLokaalMin.setOnClickListener { bump(etLokaal, -1) }
    }

    private fun setupAttributeButtons() {
        fun makeExclusive(vararg buttons: MaterialButton) {
            // maak checkable en exclusief binnen deze set
            buttons.forEach { btn ->
                btn.isCheckable = true
                btn.isChecked = false
                btn.setOnClickListener {
                    buttons.forEach { it.isChecked = false }
                    btn.isChecked = true
                }
            }
        }
        makeExclusive(binding.btnGeslachtMan, binding.btnGeslachtVrouw, binding.btnGeslachtOnbekend)
        makeExclusive(
            binding.btnLeeftijdAdult, binding.btnLeeftijdJuv, binding.btnLeeftijdGt1kj,
            binding.btnLeeftijd1kj, binding.btnLeeftijd2kj, binding.btnLeeftijd3kj, binding.btnLeeftijd4kj
        )
        makeExclusive(
            binding.btnKleedZomer, binding.btnKleedWinter, binding.btnKleedIntermediair,
            binding.btnKleedLicht, binding.btnKleedDonker, binding.btnKleedEclips
        )
        makeExclusive(
            binding.btnLocZee, binding.btnLocBranding, binding.btnLocDuinen,
            binding.btnLocCampings, binding.btnLocPolders
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
