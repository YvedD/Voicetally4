package com.yvesds.voicetally4.ui.schermen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
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
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.yvesds.voicetally4.R
import com.yvesds.voicetally4.databinding.FragmentMetadataSchermBinding
import com.yvesds.voicetally4.ui.data.MetadataForm
import com.yvesds.voicetally4.utils.io.StorageUtils
import com.yvesds.voicetally4.utils.net.ApiResponse
import com.yvesds.voicetally4.utils.net.CredentialsStore
import com.yvesds.voicetally4.utils.net.TrektellenApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Geoptimaliseerde versie:
 * - GEEN netwerk/IO in onCreateView/onViewCreated.
 * - Zwaardere UI-setup (adapters) pas na eerste frame (view.post) om snelle navigatie te garanderen.
 * - Weather ophalen blijft puur op knopactie.
 * - Upload-payload wordt pas gebouwd bij 'Verder'.
 */
class MetadataScherm : Fragment() {

    private var _binding: FragmentMetadataSchermBinding? = null
    private val binding get() = _binding!!

    // Lichte singletons
    private lateinit var weatherManager: com.yvesds.voicetally4.utils.weather.WeatherManager
    private lateinit var credentialsStore: CredentialsStore
    private val api by lazy { TrektellenApi() } // lazy: wordt NIET aangemaakt bij openen, enkel bij upload

    // Datum/tijd state
    private var selectedDate: LocalDate = LocalDate.now()
    private var selectedTimeHHmm: String =
        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    // Prefs: onthoud laatste telpost
    private val prefsName = "metadata_prefs"
    private val keyLastTelpostCode = "last_telpost_code"

    // Formatters
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

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
        val t0 = System.nanoTime()
        _binding = FragmentMetadataSchermBinding.inflate(inflater, container, false)
        weatherManager =
            com.yvesds.voicetally4.utils.weather.WeatherManager(requireContext().applicationContext)
        credentialsStore = CredentialsStore(requireContext().applicationContext)
        Log.d("PerfMeta", "onCreateView done in ${(System.nanoTime() - t0) / 1_000_000} ms")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val t0 = System.nanoTime()
        super.onViewCreated(view, savedInstanceState)

        applySystemBarInsets()
        setupDateField()
        setupTimePickerDialog()
        setupTelpostSpinner()
        setupTellersField()
        setupBottomBar()

        // Belangrijk voor snelle navigatie:
        // Adapters voor dropdowns en weersectie pas NA eerste frame zetten.
        // Zo tekent het scherm direct en vullen we de (relatief zware) TextInputLayout/AutoComplete
        // pas meteen daarna zonder de eerste draw te blokkeren.
        view.post {
            setupWeatherSectionAdapters()
            setupTypeTellingSpinner()
            setupLocatieTypeSpinner()
        }

        Log.d("PerfMeta", "onViewCreated light setup in ${(System.nanoTime() - t0) / 1_000_000} ms")
    }

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
            val utcMidnight =
                selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(getString(R.string.meta_datum))
                .setSelection(utcMidnight)
                .build()
            picker.addOnPositiveButtonClickListener { millis ->
                val local =
                    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
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
        val current = try {
            LocalTime.parse(selectedTimeHHmm, timeFmt)
        } catch (_: Exception) {
            LocalTime.now()
        }

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
            layoutParams =
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val minutePicker = NumberPicker(requireContext()).apply {
            minValue = 0
            maxValue = 59
            value = current.minute
            wrapSelectorWheel = true
            setFormatter { String.format(Locale.getDefault(), "%02d", it) }
            layoutParams =
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

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
                selectedTimeHHmm =
                    String.format(Locale.getDefault(), "%02d:%02d", hourPicker.value, minutePicker.value)
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

        val defaultIndex =
            labels.indexOf("VoiceTally Testsite").let { if (it >= 0) it else labels.lastIndex }
        val startIndex = if (savedCode != null) {
            values.indexOf(savedCode).takeIf { it >= 0 } ?: defaultIndex
        } else defaultIndex

        binding.acTelpost.setText(labels[startIndex], false)

        binding.acTelpost.setOnItemClickListener { _, _, position, _ ->
            prefs.edit { putString(keyLastTelpostCode, values[position]) }
        }
    }

    // endregion

    // region Tellers

    private fun setupTellersField() {
        if (binding.etTellers.text.isNullOrBlank()) {
            binding.etTellers.setText("Yves De Saedeleer")
        }
        binding.etTellers.doOnTextChanged { _, _, _, _ -> /* eventueel validatie */ }
    }

    // endregion

    // region Weer (ADAPTERS los gezet van knophandler om first-frame te versnellen)

    private fun setupWeatherSectionAdapters() {
        binding.acWindrichting.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                resources.getStringArray(R.array.vt4_windrichting_opties)
            )
        )
        binding.acBewolking.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                resources.getStringArray(R.array.vt4_bewolking_achtsten)
            )
        )
        binding.acNeerslag.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                resources.getStringArray(R.array.vt4_neerslag_opties)
            )
        )
        binding.acWindkracht.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                resources.getStringArray(R.array.vt4_windkracht_beaufort)
            )
        )

        // Alleen event-koppeling (licht)
        binding.btnWeerIcon.setOnClickListener {
            val fine = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (fine || coarse) {
                updateWeatherFromDeviceLocation()
            } else {
                permLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
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
                    binding.etTemperatuur.setText(
                        String.format(Locale.getDefault(), "%.1f", w.temperature)
                    )
                }

                // Bewolking in achtsten (x/8)
                val octas = weatherManager.toOctas(w.cloudcover)
                binding.acBewolking.setText("$octas/8", false)

                // Neerslag-optie uit weathercode
                binding.acNeerslag.setText(
                    weatherManager.mapWeatherCodeToNeerslagOption(w.weathercode),
                    false
                )

                // Zicht in meters
                if (w.visibility > 0) {
                    binding.etZicht.setText(w.visibility.toString())
                }

                // Windkracht (Beaufort → spinnerwaarde)
                val bft = weatherManager.toBeaufort(w.windspeed)
                binding.acWindkracht.setText(toWindkrachtOption(bft, w.windspeed), false)

                // Luchtdruk (hPa)
                if (w.pressure > 0) {
                    binding.etLuchtdruk.setText(w.pressure.toString())
                }
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
            } catch (_: SecurityException) {
            }
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
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                resources.getStringArray(R.array.vt4_type_telling_opties)
            )
        )
    }

    private fun setupLocatieTypeSpinner() {
        binding.acLocatieType.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                resources.getStringArray(R.array.vt4_locatie_type_opties)
            )
        )
    }

    // endregion

    // region Onderste knoppen

    private fun setupBottomBar() {
        binding.btnAnnuleer.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.btnVerder.setOnClickListener {
            val form = collectForm()

            // (Optioneel) payload tonen & uploaden gebeurt elders; hier geen zware taken.
            // Voor jouw flow kun je rechtstreeks navigeren:
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
        val telpostIndex =
            labels.indexOf(currentTelpostLabel).takeIf { it >= 0 } ?: labels.lastIndex
        val currentTelpostCode = values[telpostIndex]

        // Tellers
        val tellerNaam = binding.etTellers.text?.toString()?.trim().orEmpty()
        val tellerId = "3533" // voorlopig vast

        // Weer
        val windrichting = binding.acWindrichting.text?.toString()?.trim().orEmpty()
        val temperatuurC =
            binding.etTemperatuur.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
        val bewolking = binding.acBewolking.text?.toString()?.trim().orEmpty()
        val neerslag = binding.acNeerslag.text?.toString()?.trim().orEmpty()
        val zichtMeters =
            binding.etZicht.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
        val windkrachtBft = binding.acWindkracht.text?.toString()?.trim().orEmpty()
        val luchtdrukHpa =
            binding.etLuchtdruk.text?.toString()?.replace(',', '.')?.toDoubleOrNull()

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
            zichtKm = zichtMeters, // (meters, legacy naam in dataklasse)
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

    // region Helpers (UI / Upload popups voor debugging — NIET gebruikt bij openen)

    private fun showPayloadDialog(title: String, json: String) {
        val tv = TextView(requireContext()).apply {
            text = json
            setTextIsSelectable(true)
            setPadding(24, 16, 24, 16)
        }
        val scroll = ScrollView(requireContext()).apply { addView(tv) }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun ensureCredsThen(block: () -> Unit) {
        if (!credentialsStore.hasCredentials()) {
            showCredentialsDialog(block)
        } else block()
    }

    private fun showCredentialsDialog(onSaved: () -> Unit) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 8)
        }

        fun field(hint: String, pw: Boolean = false): Pair<TextInputLayout, TextInputEditText> {
            val til = TextInputLayout(requireContext()).apply { this.hint = hint }
            val et = TextInputEditText(requireContext()).apply {
                inputType = if (pw)
                    android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                else
                    android.text.InputType.TYPE_CLASS_TEXT
                maxLines = 1
            }
            til.addView(et)
            return til to et
        }

        val (tilUser, etUser) = field("Gebruikersnaam")
        val (tilPass, etPass) = field("Wachtwoord", pw = true)
        container.addView(tilUser)
        container.addView(tilPass)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Inloggen Trektellen (Basic Auth)")
            .setView(container)
            .setPositiveButton("Opslaan") { _, _ ->
                val u = etUser.text?.toString()?.trim().orEmpty()
                val p = etPass.text?.toString()?.trim().orEmpty()
                credentialsStore.save(u, p)
                onSaved()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // endregion
}
