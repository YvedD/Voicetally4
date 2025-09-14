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
import com.yvesds.voicetally4.utils.upload.CountsPayloadBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MetadataScherm : Fragment() {

    private var _binding: FragmentMetadataSchermBinding? = null
    private val binding get() = _binding!!

    private lateinit var weatherManager: com.yvesds.voicetally4.utils.weather.WeatherManager
    private lateinit var credentialsStore: CredentialsStore
    private val api by lazy { TrektellenApi() }

    // Datum/tijd state
    private var selectedDate: LocalDate = LocalDate.now()
    private var selectedTimeHHmm: String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    // Prefs: onthoud laatste telpost
    private val prefsName = "metadata_prefs"
    private val keyLastTelpostCode = "last_telpost_code"

    // Formatters
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            updateWeatherFromDeviceLocation()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMetadataSchermBinding.inflate(inflater, container, false)
        weatherManager = com.yvesds.voicetally4.utils.weather.WeatherManager(requireContext().applicationContext)
        credentialsStore = CredentialsStore(requireContext().applicationContext)
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

    // region Tijd

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
                if (w.visibility > 0) {
                    binding.etZicht.setText(w.visibility.toString()) // meters
                }
                val bft = weatherManager.toBeaufort(w.windspeed)
                binding.acWindkracht.setText(toWindkrachtOption(bft, w.windspeed), false)
                if (w.pressure > 0) {
                    binding.etLuchtdruk.setText(w.pressure.toString())
                }
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
            } catch (_: SecurityException) { }
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

    // region Onderste knoppen (hier ook de UPDATE-test koppelen)

    private fun setupBottomBar() {
        binding.btnAnnuleer.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.btnVerder.setOnClickListener {
            val form = collectForm()

            // --- UPDATE-DEMO: hardcoded parameters voor jouw test ---
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val beginSec = today.atTime(23, 5).atZone(zone).toEpochSecond()
            val eindSec  = today.atTime(23, 6).atZone(zone).toEpochSecond()

            val telpostId = "5177" // VoiceTally Testsite
            val onlineId  = "3147070" // bestaande telling die je wil bijwerken

            val payload = CountsPayloadBuilder.buildUpdateDemo(
                form = form,
                beginSec = beginSec,
                eindSec = eindSec,
                weerOpmerking = binding.etWeerOpmerking.text?.toString().orEmpty(),
                telpostId = telpostId,
                onlineId = onlineId
            )
            // -------------------------------------------------------

            showPayloadAndUpload(payload)
        }
    }

    private fun showPayloadAndUpload(payload: String) {
        val tv = TextView(requireContext()).apply {
            text = payload
            setTextIsSelectable(true)
            setPadding(24, 16, 24, 16)
        }
        val scroll = ScrollView(requireContext()).apply { addView(tv) }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Te uploaden JSON (update-demo)")
            .setView(scroll)
            .setPositiveButton("Uploaden") { _, _ -> ensureCredsThenUpload(payload) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun ensureCredsThenUpload(payload: String) {
        if (!credentialsStore.hasCredentials()) {
            showCredentialsDialog { doUpload(payload) }
        } else {
            doUpload(payload)
        }
    }

    private fun doUpload(payload: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            StorageUtils.saveJsonToPublicDocuments(
                requireContext(),
                "VoiceTally4/serverdata",
                "last_upload.json",
                payload
            )

            val (user, pass) = credentialsStore.get()
            val resp: ApiResponse = withContext(Dispatchers.IO) {
                if (user.isNullOrBlank() || pass.isNullOrBlank()) {
                    ApiResponse(false, 401, """{"message":"missing credentials"}""")
                } else {
                    TrektellenApi().postCountsSaveBasicAuth(user, pass, payload)
                }
            }

            StorageUtils.saveJsonToPublicDocuments(
                requireContext(),
                "VoiceTally4/serverdata",
                "last_upload_response.json",
                resp.body
            )

            val tv = TextView(requireContext()).apply {
                text = resp.body
                setTextIsSelectable(true)
                setPadding(24, 16, 24, 16)
            }
            val scroll = ScrollView(requireContext()).apply { addView(tv) }

            val title = "Server response (HTTP ${resp.code})"
            val messageInfo = "\n\n↳ Opgeslagen als:\n" +
                    "Documents/VoiceTally4/serverdata/last_upload.json\n" +
                    "Documents/VoiceTally4/serverdata/last_upload_response.json"

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(scroll)
                .setMessage(messageInfo)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    // endregion

    // region Collect form

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
            bewolkingAchtsten = bewolking,     // "x/8" in UI → mappen we in payload naar "x"
            neerslag = neerslag,
            zichtKm = zichtMeters,              // meters (legacy naam)
            windkrachtBft = windkrachtBft,      // "4bf" etc. → mappen we in payload naar cijfer
            luchtdrukHpa = luchtdrukHpa,
            opmerkingen = binding.etOpmerkingen.text?.toString()?.trim().orEmpty()
        )
    }

    // endregion

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
