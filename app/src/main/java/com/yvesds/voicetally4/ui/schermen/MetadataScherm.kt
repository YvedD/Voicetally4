package com.yvesds.voicetally4.ui.schermen

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.TextView
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
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MetadataScherm : Fragment() {

    private var _binding: FragmentMetadataSchermBinding? = null
    private val binding get() = _binding!!

    private lateinit var weatherManager: WeatherManager

    // Formatters
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    // Datum/tijd state (geen afronding)
    private var selectedDate: LocalDate = LocalDate.now()
    private var selectedTimeHHmm: String = LocalTime.now().format(timeFmt)

    // Prefs
    private val prefsName = "metadata_prefs"
    private val keyLastTelpostCode = "last_telpost_code"

    // Export preview storage
    private val exportPrefsName = "export_prefs"
    private val keyLastPreviewJson = "last_preview_json"

    // Permissions
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
        setupTimePickerDialog()
        setupTelpostSpinner()
        setupTellersField()
        setupWeatherSection()
        setupTypeTellingSpinner()
        setupLocatieTypeSpinner()
        setupBottomBar()
    }

    /** Zorgt dat bottomBar boven de systeem-navigatiebalk blijft. */
    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootConstraint) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
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

    // region Tijd (custom popup met twee spinners)

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
            minValue = 0; maxValue = 23; value = current.hour; wrapSelectorWheel = true
            setFormatter { String.format(Locale.getDefault(), "%02d", it) }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val minutePicker = NumberPicker(requireContext()).apply {
            minValue = 0; maxValue = 59; value = current.minute; wrapSelectorWheel = true
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
                binding.acWindrichting.setText(weatherManager.toCompass(w.winddirection), false)
                if (!w.temperature.isNaN()) {
                    binding.etTemperatuur.setText(String.format(Locale.getDefault(), "%.1f", w.temperature))
                }
                val octas = weatherManager.toOctas(w.cloudcover)
                binding.acBewolking.setText("$octas/8", false)
                binding.acNeerslag.setText(weatherManager.mapWeatherCodeToNeerslagOption(w.weathercode), false)
                if (w.visibility > 0) binding.etZicht.setText(w.visibility.toString()) // meters
                val bft = weatherManager.toBeaufort(w.windspeed)
                binding.acWindkracht.setText(toWindkrachtOption(bft, w.windspeed), false)
                if (w.pressure > 0) binding.etLuchtdruk.setText(w.pressure.toString())
            }
        }
    }

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
            } catch (_: SecurityException) { /* no-op */ }
        }
        return best
    }

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
        binding.btnVerder.setOnClickListener { onVerderClicked() }
    }

    private fun onVerderClicked() {
        // Bouw JSON preview volgens jouw regels
        val preview = buildExportPreviewJson()

        // Bewaar tussentijds
        requireContext().getSharedPreferences(exportPrefsName, Context.MODE_PRIVATE)
            .edit { putString(keyLastPreviewJson, preview) }

        // Toon popup met JSON en opties
        val tv = TextView(requireContext()).apply {
            text = preview
            setTextIsSelectable(true)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            if (Build.VERSION.SDK_INT >= 28) {
                typeface = android.graphics.Typeface.MONOSPACE
            } else {
                paint.isUnderlineText = false
            }
            setPadding(24, 16, 24, 16)
        }
        val scroll = ScrollView(requireContext()).apply { addView(tv) }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Voorbeeld export (1 item)")
            .setView(scroll)
            .setPositiveButton("Verder") { _, _ ->
                val form = collectForm()
                findNavController().navigate(R.id.action_metadataScherm_to_soortSelectieScherm)
            }
            .setNeutralButton("Kopiëren") { _, _ ->
                copyToClipboard("VoiceTally JSON", preview)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    /**
     * Stelt een top-level JSONArray samen met 1 object,
     * en vult elk veld conform jouw instructies.
     *
     * Let op: 'eindtijd' moet na 'begintijd' liggen → we zetten die op +60s.
     */
    private fun buildExportPreviewJson(): String {
        val labels = resources.getStringArray(R.array.vt4_telpost_labels)
        val values = resources.getStringArray(R.array.vt4_telpost_values)
        val telpostLabel = binding.acTelpost.text?.toString().orEmpty()
        val telpostIndex = labels.indexOf(telpostLabel).takeIf { it >= 0 } ?: 0
        val telpostCode = values[telpostIndex]

        val begintijdSec = Instant.now().epochSecond          // bij 'Verder'
        val eindtijdSec = begintijdSec + 60                   // altijd 1 minuut later

        val obj = JSONObject().apply {
            put("externid", "Android App 1.8.45")
            put("timezoneid", "Europe/Brussels")
            put("bron", "4")
            put("_id", "")
            put("tellingid", "")
            put("telpostid", telpostCode)
            put("begintijd", begintijdSec.toString())
            put("eindtijd", eindtijdSec.toString())
            put("tellers", binding.etTellers.text?.toString().orEmpty())
            put("weer", binding.etWeerOpmerking.text?.toString().orEmpty())
            put("windrichting", binding.acWindrichting.text?.toString().orEmpty())
            put("windkracht", binding.acWindkracht.text?.toString().orEmpty())
            put("temperatuur", binding.etTemperatuur.text?.toString().orEmpty())
            put("bewolking", binding.acBewolking.text?.toString().orEmpty())
            put("bewolkinghoogte", "")
            put("neerslag", binding.acNeerslag.text?.toString().orEmpty())
            put("duurneerslag", "")
            put("zicht", binding.etZicht.text?.toString().orEmpty())
            put("tellersactief", "")
            put("tellersaanwezig", "")
            put("typetelling", "all")        // voorlopig vast zoals gevraagd
            put("metersnet", "")
            put("geluid", "")
            put("opmerkingen", binding.etOpmerkingen.text?.toString().orEmpty())
            put("onlineid", "")
            put("HYDRO", "")
            put("hpa", "")
            put("equipment", "")
            put("uuid", "Trektellen_Android_1.8.45_d337303b-35a0-4d6f-aa98-8c1a08e01647")
            put("uploadtijdstip", "")
            put("nrec", "4")
            put("nsoort", "4")
            put("data", SAMPLE_DATA_ARRAY)
        }

        val top = JSONArray().put(obj)
        return top.toString(2) // pretty print
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

        val labels = resources.getStringArray(R.array.vt4_telpost_labels)
        val values = resources.getStringArray(R.array.vt4_telpost_values)
        val currentTelpostLabel = binding.acTelpost.text?.toString().orEmpty()
        val telpostIndex = labels.indexOf(currentTelpostLabel).takeIf { it >= 0 } ?: labels.lastIndex
        val currentTelpostCode = values[telpostIndex]

        val tellerNaam = binding.etTellers.text?.toString()?.trim().orEmpty()
        val tellerId = "3533"

        val windrichting = binding.acWindrichting.text?.toString()?.trim().orEmpty()
        val temperatuurC = binding.etTemperatuur.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
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
            zichtKm = zichtMeters, // inhoud = meters (legacy veldnaam)
            windkrachtBft = windkrachtBft,
            luchtdrukHpa = luchtdrukHpa,
            opmerkingen = binding.etOpmerkingen.text?.toString()?.trim().orEmpty()
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Volledige 'data'-array uit jouw voorbeeld, ongewijzigd (voor preview). */
        private val SAMPLE_DATA_ARRAY: JSONArray by lazy {
            val raw = """
                [
                    {
                        "_id": "14",
                        "tellingid": "28",
                        "soortid": "31",
                        "aantal": "1",
                        "richting": "w",
                        "aantalterug": "2",
                        "richtingterug": "o",
                        "sightingdirection": "NW",
                        "lokaal": "3",
                        "aantal_plus": "0",
                        "aantalterug_plus": "0",
                        "lokaal_plus": "0",
                        "markeren": "1",
                        "markerenlokaal": "1",
                        "geslacht": "M",
                        "leeftijd": "A",
                        "kleed": "L",
                        "opmerkingen": "Remarks",
                        "trektype": "R",
                        "teltype": "C",
                        "location": "",
                        "height": "L",
                        "tijdstip": "1756721135",
                        "groupid": "14",
                        "uploadtijdstip": "2025-09-01 10:05:39",
                        "totaalaantal": "3"
                    },
                    {
                        "_id": "13",
                        "tellingid": "28",
                        "soortid": "254",
                        "aantal": "300",
                        "richting": "",
                        "aantalterug": "0",
                        "richtingterug": "",
                        "sightingdirection": "",
                        "lokaal": "0",
                        "aantal_plus": "0",
                        "aantalterug_plus": "0",
                        "lokaal_plus": "0",
                        "markeren": "0",
                        "markerenlokaal": "0",
                        "geslacht": "",
                        "leeftijd": "",
                        "kleed": "",
                        "opmerkingen": "",
                        "trektype": "",
                        "teltype": "",
                        "location": "",
                        "height": "M",
                        "tijdstip": "1756210093",
                        "groupid": "13",
                        "uploadtijdstip": "2025-09-01 10:05:39",
                        "totaalaantal": "300"
                    },
                    {
                        "_id": "12",
                        "tellingid": "28",
                        "soortid": "105",
                        "aantal": "1",
                        "richting": "",
                        "aantalterug": "0",
                        "richtingterug": "",
                        "sightingdirection": "",
                        "lokaal": "0",
                        "aantal_plus": "0",
                        "aantalterug_plus": "0",
                        "lokaal_plus": "0",
                        "markeren": "0",
                        "markerenlokaal": "0",
                        "geslacht": "",
                        "leeftijd": "",
                        "kleed": "",
                        "opmerkingen": "",
                        "trektype": "",
                        "teltype": "",
                        "location": "",
                        "height": "M",
                        "tijdstip": "1756209152",
                        "groupid": "12",
                        "uploadtijdstip": "2025-09-01 10:05:39",
                        "totaalaantal": "1"
                    },
                    {
                        "_id": "11",
                        "tellingid": "28",
                        "soortid": "94",
                        "aantal": "1",
                        "richting": "",
                        "aantalterug": "0",
                        "richtingterug": "",
                        "sightingdirection": "ENE",
                        "lokaal": "0",
                        "aantal_plus": "0",
                        "aantalterug_plus": "0",
                        "lokaal_plus": "0",
                        "markeren": "0",
                        "markerenlokaal": "0",
                        "geslacht": "",
                        "leeftijd": "",
                        "kleed": "",
                        "opmerkingen": "",
                        "trektype": "",
                        "teltype": "",
                        "location": "",
                        "height": "M",
                        "tijdstip": "1756208899",
                        "groupid": "11",
                        "uploadtijdstip": "2025-09-01 10:05:39",
                        "totaalaantal": "1"
                    }
                ]
            """.trimIndent()
            JSONArray(raw)
        }
    }
}
