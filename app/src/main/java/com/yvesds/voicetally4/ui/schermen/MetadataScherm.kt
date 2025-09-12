package com.yvesds.voicetally4.ui.schermen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.yvesds.voicetally4.R
import com.yvesds.voicetally4.databinding.FragmentMetadataSchermBinding
import com.yvesds.voicetally4.ui.data.MetadataForm
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Tussenscherm om meta-data te kiezen/ingevuld te krijgen vóór de soortselectie.
 * - Dropdowns (project, locatie, weer, methode)
 * - Datum- en tijdkiezer (Material pickers)
 * - Free text: waarnemer en opmerkingen
 * - "Verder" navigeert naar SoortSelectieScherm (data-opslag/serialisatie doen we in de volgende stap/thread)
 */
class MetadataScherm : Fragment() {

    private var _binding: FragmentMetadataSchermBinding? = null
    private val binding get() = _binding!!

    // Eenvoudige state (in-memory). Persistenter maken we later in de volgende stap.
    private var selectedDate: LocalDate = LocalDate.now()
    private var selectedTime: LocalTime = LocalTime.now()

    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE       // 2025-09-12
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")   // 19:35

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMetadataSchermBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Dropdowns vullen met resources-arrays
        setupDropdown(binding.acProject, R.array.vt4_project_opties)
        setupDropdown(binding.acLocatieType, R.array.vt4_locatie_type_opties)
        setupDropdown(binding.acWeer, R.array.vt4_weer_opties)
        setupDropdown(binding.acMethode, R.array.vt4_methode_opties)

        // Datum/Tijd initialiseren op nu
        binding.btnDatum.text = selectedDate.format(dateFmt)
        binding.btnTijd.text = selectedTime.format(timeFmt)

        // Pickers
        binding.btnDatum.setOnClickListener { showDatePicker() }
        binding.btnTijd.setOnClickListener { showTimePicker() }

        // Verder → (voor nu) gewoon door naar soortselectie; in volgende stap serialiseren/opslaan
        binding.btnVerder.setOnClickListener {
            val form = collectForm()
            // TODO: in volgende thread: form opslaan (prefs/json/bestand) en/of doorgeven via SafeArgs/VM
            findNavController().navigate(R.id.action_metadataScherm_to_soortSelectieScherm)
        }

        // Annuleer/terug
        binding.btnAnnuleer.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupDropdown(view: android.widget.AutoCompleteTextView, arrayRes: Int) {
        val items = resources.getStringArray(arrayRes)
        view.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, items))
    }

    private fun showDatePicker() {
        val utcMidnight = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.meta_kies_datum_title)
            .setSelection(utcMidnight)
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            val local = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
            selectedDate = local
            binding.btnDatum.text = selectedDate.format(dateFmt)
        }
        picker.show(parentFragmentManager, "datePicker")
    }

    private fun showTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(selectedTime.hour)
            .setMinute(selectedTime.minute)
            .setTitleText(R.string.meta_kies_tijd_title)
            .build()
        picker.addOnPositiveButtonClickListener {
            selectedTime = LocalTime.of(picker.hour, picker.minute)
            binding.btnTijd.text = selectedTime.format(timeFmt)
        }
        picker.show(parentFragmentManager, "timePicker")
    }

    private fun collectForm(): MetadataForm {
        val epochMillis = selectedDate
            .atTime(selectedTime)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        return MetadataForm(
            observer = binding.etWaarnemer.text?.toString()?.trim().orEmpty(),
            project = binding.acProject.text?.toString()?.trim().orEmpty(),
            locatieType = binding.acLocatieType.text?.toString()?.trim().orEmpty(),
            datumEpochMillis = epochMillis,
            weer = binding.acWeer.text?.toString()?.trim().orEmpty(),
            methode = binding.acMethode.text?.toString()?.trim().orEmpty(),
            opmerkingen = binding.etOpmerkingen.text?.toString()?.trim().orEmpty()
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
