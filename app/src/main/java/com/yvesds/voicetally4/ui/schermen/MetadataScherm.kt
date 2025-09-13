package com.yvesds.voicetally4.ui.schermen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yvesds.voicetally4.R
import com.yvesds.voicetally4.databinding.FragmentMetadataSchermBinding
import com.yvesds.voicetally4.ui.data.MetadataForm
import com.yvesds.voicetally4.utils.weather.WeatherManager
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MetadataScherm : Fragment() {

    private var _binding: FragmentMetadataSchermBinding? = null
    private val binding get() = _binding!!

    // Geen DI nodig: lokaal aanmaken
    private lateinit var weatherManager: WeatherManager

    // Formatters
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    // Datum/tijd state (géén afronding meer)
    private var selectedDate: LocalDate = LocalDate.now()
    private var selectedTimeHHmm: String = LocalTime.now().format(timeFmt) // HH:mm

    // Prefs: onthoud laatste telpost
    private val prefsName = "metadata_prefs"
    private val keyLastTelpostCode = "last_telpost_code"

    // Runtime permission launcher
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) updateWeatherFromDeviceLocation()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMetadataSchermBinding.inflate(inflater, container, false)
        weatherManager = WeatherManager(requireContext().applicationContext)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applySystemBarInsets()

        setupDateField()
        setupTimePickerDialog()          // minuten per 1; 59↔00 draagt uur over
        setupTelpostSpinner()
        setupTellersField()
        setupWeatherSection()            // icoonknop “Auto”
        setupTypeTellingSpinner()
        setupLocatieTypeSpinner()
        setupBottomBar()
    }

    /** Zorgt dat bottomBar boven de systeem-navigatiebalk blijft. */
    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootConstraint) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Schuif de bottombar omhoog met de hoogte van de nav bar
            binding.bottomBar.updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomMargin = sys.bottom
            }
            insets
        }
    }

    // region Datum

    private fun setupDateField() {
        binding.etDatum.setText(selectedDate.format(dateFmt))
        val openDatePicker: (View) -> Unit = {
            val utcMidnight = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(getString(R.string.meta_datum))
                .setSelection(utcMidnight)
                .build()
            picker.addOnPositiveButtonClickListener { millis ->
                val local = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                selectedDate = local
                binding.etDatum.setText(selectedDate.format(dateFmt))
            }
            picker.show(parentFragmentManager, "datePicker")
        }
        binding.etDatum.setOnClickListener(openDatePicker)
    }

    // endregion

    // region Tijd (custom popup met twee spinners: uur & minuten per 1)

    private fun setupTimePickerDialog() {
        binding.etTijd.setText(selectedTimeHHmm)
        binding.etTijd.setOnClickListener { openTimeSpinnerDialog() }
    }

    private fun openTimeSpinnerDialog() {
        val current = try { LocalTime.parse(selectedTimeHHmm, timeFmt) } catch (_: Exception) { LocalTime.now() }

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 8, 16, 8)
        }

        val hourPicker = NumberPicker(requireContext()).apply {
            minValue = 0
            maxValue = 23
            value = current.hour
            wrapSelectorWheel = true
            setFormatter { String.format(Locale.getDefault(), "%02d", it) }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val minutePicker = NumberPicker(requireContext()).apply {
            minValue = 0
            maxValue = 59
            value = current.minute
            wrapSelectorWheel = true
            setFormatter { String.format(Locale.getDefault(), "%02d", it) }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        // 59 -> 00 verhoogt uur; 00 -> 59 verlaagt uur
        minutePicker.setOnValueChangedListener { _, oldVal, newVal ->
            if (oldVal == 59 && newVal == 0) hourPicker.value = (hourPicker.value + 1) % 24
            else if (oldVal == 0 && newVal == 59) hourPicker.value = (hourPicker.value + 23) % 24
        }

        container.addView(hourPicker)
        container.addView(minutePicker)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.meta_tijd))
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                selectedTimeHHmm = String.format(Locale.getDefault(), "%02d:%02d", hourPicker.value, minutePicker.value)
                binding.etTijd.setText(selectedTimeHHmm)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // endregion

    // region Telpost

    private fun setupTelpostSpinner() {
        val labels = resources.getStringArray(R.array.vt4_telpost_labels)
        val values = resources.getStringArray(R.array.vt4_telpost_values)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
        binding.acTelpost.setAdapter(adapter)

        val prefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val savedCode = prefs.getString(keyLastTelpostCode, null)

        val defaultIndex = labels.indexOf("VoiceTally Testsite").let { if (it >= 0) it else labels.lastIndex }
        val startIndex = savedCode?.let { values.indexOf(it).takeIf { i -> i >= 0 } } ?: defaultIndex

        binding.acTelpost.setText(labels[startIndex], false)
        binding.acTelpost.setOnItemClickListener { _, _, position, _ ->
            prefs.edit { putString(keyLastTelpostCode, values[position]) }
        }
    }

    // endregion

    // region Tellers

    private fun setupTellersField() {
        if (binding.etTellers.text.isNullOrBlank()) binding.etTellers.setText("Yves De Saedeleer")
        binding.etTellers.doOnTextChanged { _, _, _, _ -> }
    }

    // endregion

    // region Weer

    private fun setupWeatherSection() {
        // Adapters
        binding.acWindrichting.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, resources.getStringArray(R.array.vt4_windrichting_opties))
        )
        binding.acBewolking.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, resources.getStringArray(R.array.vt4_bewolking_achtsten))
        )
        binding.acNeerslag.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, resources.getStringArray(R.array.vt4_neerslag_opties))
        )
        binding.acWindkracht.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, resources.getStringArray(R.array.vt4_windkracht_beaufort))
        )

        // Icoonknop “Auto”
        binding.btnWeerIcon.setOnClickListener {
            val fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fine || coarse) {
                updateWeatherFromDeviceLocation()
            } else {
                permLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
    }

    private fun updateWeatherFromDeviceLocation() {
        val loc = getBestLastKnownLocation(requireContext()) ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val w = weatherManager.fetchCurrentWeather(loc.latitude, loc.longitude)
            if (w != null) {
                // Windrichting (kompas)
                binding.acWindrichting.setText(weatherManager.toCompass(w.winddirection), false)

                // Temperatuur (°C, 1 decimaal)
                if (!w.temperature.isNaN()) {
                    binding.etTemperatuur.setText(String.format(Locale.getDefault(), "%.1f", w.temperature))
                }

                // Bewolking in achtsten (x/8)
                val octas = weatherManager.toOctas(w.cloudcover)
                binding.acBewolking.setText("$octas/8", false)

                // Neerslag-optie uit weathercode
                binding.acNeerslag.setText(weatherManager.mapWeatherCodeToNeerslagOption(w.weathercode), false)

                // Zicht in MÉTERS
                if (w.visibility > 0) binding.etZicht.setText(w.visibility.toString())

                // Windkracht (Beaufort → spinnerwaarde)
                val bft = weatherManager.toBeaufort(w.windspeed)
                binding.acWindkracht.setText(toWindkrachtOption(bft, w.windspeed), false)

                // Luchtdruk (hPa)
                if (w.pressure > 0) binding.etLuchtdruk.setText(w.pressure.toString())
            }
        }
    }

    /** Probeer GPS en netwerk; pak de meest recente lastKnown. */
    private fun getBestLastKnownLocation(ctx: Context): Location? {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var best: Location? = null
        for (p in providers) {
            try {
                val l = lm.getLastKnownLocation(p) ?: continue
                if (best == null || (l.time > best!!.time)) best = l
            } catch (_: SecurityException) { /* permission niet verleend */ }
        }
        return best
    }

    /** Zet Beaufort naar de exacte spinner-items: var, <1bf, 1bf..11bf, >11bf */
    private fun toWindkrachtOption(bft: Int, speedKmh: Double): String = when {
        speedKmh < 1.0 -> "<1bf"
        bft <= 0 -> "var"
        bft in 1..11 -> "${bft}bf"
        else -> ">11bf"
    }

    // endregion

    // region Type telling & Locatie type

    private fun setupTypeTellingSpinner() {
        binding.acTypeTelling.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, resources.getStringArray(R.array.vt4_type_telling_opties))
        )
    }

    private fun setupLocatieTypeSpinner() {
        binding.acLocatieType.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, resources.getStringArray(R.array.vt4_locatie_type_opties))
        )
    }

    // endregion

    // region Onderste knoppen

    private fun setupBottomBar() {
        binding.btnAnnuleer.setOnClickListener { findNavController().popBackStack() }
        binding.btnVerder.setOnClickListener {
            val form = collectForm()
            // TODO: bewaar 'form' waar jij export verwacht
            findNavController().navigate(R.id.action_metadataScherm_to_soortSelectieScherm)
        }
    }

    private fun collectForm(): MetadataForm {
        val hhmm = binding.etTijd.text?.toString()?.takeIf { it.matches(Regex("\\d{2}:\\d{2}")) }
            ?: selectedTimeHHmm
        val localTime = LocalTime.parse(hhmm, timeFmt)
        val epochMillis = selectedDate
            .atTime(localTime)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // Telpost
        val labels = resources.getStringArray(R.array.vt4_telpost_labels)
        val values = resources.getStringArray(R.array.vt4_telpost_values)
        val currentTelpostLabel = binding.acTelpost.text?.toString().orEmpty()
        val telpostIndex = labels.indexOf(currentTelpostLabel).takeIf { it >= 0 } ?: labels.lastIndex
        val currentTelpostCode = values[telpostIndex]

        // Tellers
        val tellerNaam = binding.etTellers.text?.toString()?.trim().orEmpty()
        val tellerId = "3533" // voorlopig vast

        // Weer
        val windrichting = binding.acWindrichting.text?.toString()?.trim().orEmpty()
        val temperatuurC = binding.etTemperatuur.text?.toString()?.toDoubleOrNull()
        val bewolking = binding.acBewolking.text?.toString()?.trim().orEmpty()
        val neerslag = binding.acNeerslag.text?.toString()?.trim().orEmpty()
        val zichtMeters = binding.etZicht.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
        val windkrachtBft = binding.acWindkracht.text?.toString()?.trim().orEmpty()
        val luchtdrukHpa = binding.etLuchtdruk.text?.toString()?.replace(',', '.')?.toDoubleOrNull()

        return MetadataForm(
            typeTelling = binding.acTypeTelling.text?.toString()?.trim().orEmpty(),
            locatieType = binding.acLocatieType.text?.toString()?.trim().orEmpty(),
            datumEpochMillis = epochMillis,
            timeHHmm = hhmm,
            telpostLabel = currentTelpostLabel,
            telpostCode = currentTelpostCode,
            tellersNaam = tellerNaam,
            tellersId = tellerId,
            windrichting = windrichting,
            temperatuurC = temperatuurC,
            bewolkingAchtsten = bewolking,
            neerslag = neerslag,
            zichtKm = zichtMeters, // naam blijft zo in model; inhoud=meters
            windkrachtBft = windkrachtBft,
            luchtdrukHpa = luchtdrukHpa,
            opmerkingen = binding.etOpmerkingen.text?.toString()?.trim().orEmpty()
        )
    }

    // endregion

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
