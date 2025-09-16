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
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.yvesds.voicetally4.R
import com.yvesds.voicetally4.databinding.FragmentMetadataSchermBinding
import com.yvesds.voicetally4.ui.data.MetadataForm
import com.yvesds.voicetally4.utils.codes.CodesIndex
import com.yvesds.voicetally4.utils.codes.CodesRepository
import com.yvesds.voicetally4.utils.io.StorageUtils
import com.yvesds.voicetally4.utils.net.CredentialsStore
import com.yvesds.voicetally4.utils.net.TrektellenApi
import com.yvesds.voicetally4.utils.weather.WeatherManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * MetadataScherm:
 * - Registreert bij openen 1x het tijdstip, gebruikt voor begintijd/eindtijd (epoch seconds).
 * - 'Verder' -> counts_save met alle metadata, nrec/nsoort = "0", data = [].
 * - Response wordt opgeslagen naar /Documents/VoiceTally4/serverdata/counts_save_last.json
 * - Basic Auth met CredentialsStore (dialoog als credentials ontbreken).
 * - Geen locatie-type spinner meer.
 * - Nieuw: als codes.json ontbreekt, toon dialoog om online te downloaden (Basic Auth of fallback-nood-URL).
 */
class MetadataScherm : Fragment() {

    private var _binding: FragmentMetadataSchermBinding? = null
    private val binding get() = _binding!!

    private lateinit var weatherManager: WeatherManager

    // Tijdstempels
    private var selectedDate: LocalDate = LocalDate.now()
    private var selectedTimeHHmm: String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    private var screenEnterEpochSec: Long = 0L // exact moment dat gebruiker dit scherm opent

    // Prefs: onthoud laatste telpost
    private val prefsName = "metadata_prefs"
    private val keyLastTelpostCode = "last_telpost_code"

    // Upload prefs: bewaar laatst ontvangen IDs
    private val uploadPrefs = "upload_prefs"
    private val keyOnlineId = "last_onlineid"
    private val keyTellingId = "last_tellingid"

    // Formatters
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val uploadTsFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    // Runtime permission launcher
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted && isAdded && _binding != null) {
            updateWeatherFromDeviceLocation()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMetadataSchermBinding.inflate(inflater, container, false)
        weatherManager = WeatherManager(requireContext().applicationContext)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Registreer exact moment van binnenkomst
        screenEnterEpochSec = Instant.now().epochSecond

        applySystemBarInsets()
        setupDateField()
        setupTimePickerDialog()
        setupTelpostSpinner()
        setupTellersField()
        setupWeatherSpinners()
        hookWeatherButton()
        setupTypeTellingSpinner()
        setupBottomBar()

        // ---- NIEUW: on-demand codes laden of downloaden indien nodig ----
        lifecycleScope.launch {
            // Probeer lokaal (Documents of assets) te laden zonder UI-blokkering
            CodesIndex.ensureLoadedAsync(requireContext())
            if (!CodesIndex.isReady()) {
                promptDownloadCodes()
            }
        }
    }

    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootConstraint) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.bottomBar.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
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
                _binding?.etDatum?.setText(selectedDate.format(dateFmt))
            }
            picker.show(parentFragmentManager, "datePicker")
        }
        binding.etDatum.setOnClickListener(openDatePicker)
    }
    // endregion

    // region Tijd (custom dialog met 2 pickers)
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
            minValue = 0; maxValue = 23
            value = current.hour
            wrapSelectorWheel = true
            setFormatter { String.format(Locale.getDefault(), "%02d", it) }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val minutePicker = NumberPicker(requireContext()).apply {
            minValue = 0; maxValue = 59
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
                _binding?.etTijd?.setText(selectedTimeHHmm)
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

    // region Weer - spinners + knop
    private fun setupWeatherSpinners() {
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
    }

    private fun hookWeatherButton() {
        binding.btnWeerIcon.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
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
        val ctx = context ?: return
        val loc = getBestLastKnownLocation(ctx) ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            val w = weatherManager.fetchCurrentWeather(loc.latitude, loc.longitude)
            withContext(Dispatchers.Main) {
                val b = _binding ?: return@withContext
                if (!isAdded) return@withContext

                if (w != null) {
                    b.acWindrichting.setText(weatherManager.toCompass(w.winddirection), false)
                    if (!w.temperature.isNaN()) {
                        b.etTemperatuur.setText(String.format(Locale.getDefault(), "%.1f", w.temperature))
                    }
                    val octas = weatherManager.toOctas(w.cloudcover)
                    b.acBewolking.setText("$octas/8", false)
                    b.acNeerslag.setText(weatherManager.mapWeatherCodeToNeerslagOption(w.weathercode), false)

                    if (w.visibility != 0) {
                        b.etZicht.setText(w.visibility.toString())
                    }
                    val bft = weatherManager.toBeaufort(w.windspeed)
                    b.acWindkracht.setText(toWindkrachtOption(bft, w.windspeed), false)
                    if (w.pressure > 0) {
                        b.etLuchtdruk.setText(w.pressure.toString())
                    }
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
            } catch (_: SecurityException) {}
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

    // region Type telling
    private fun setupTypeTellingSpinner() {
        val labels = listOf(
            "alle soorten",
            "zeetrek",
            "Ooievaars en roofvogels",
            "landtrek op kusttelpost"
        )
        binding.acTypeTelling.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
        )
        binding.acTypeTelling.setText("alle soorten", false)
    }
    // endregion

    // region Onderste knoppen
    private fun setupBottomBar() {
        binding.btnAnnuleer.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnVerder.setOnClickListener {
            ensureCredentialsThen {
                val b = _binding ?: return@ensureCredentialsThen
                val form = collectForm()

                lifecycleScope.launch(Dispatchers.IO) {
                    val payload = buildCountsPayloadFromForm(form)
                    val response = TrektellenApi.postCountsSaveBasicAuth(requireContext(), payload)

                    val savedPath = try {
                        StorageUtils.saveStringToPublicDocuments(
                            context = requireContext(),
                            subDir = "serverdata",
                            fileName = "counts_save_last.json",
                            content = response.body
                        )
                        savedPathToHuman(
                            StorageUtils.getPublicAppDir(requireContext(), "serverdata").absolutePath,
                            "counts_save_last.json"
                        )
                    } catch (_: Throwable) {
                        null
                    }

                    withContext(Dispatchers.Main) {
                        val bind = _binding
                        if (bind == null || !isAdded) return@withContext

                        if (!response.ok) {
                            showErrorDialog(
                                "Upload mislukt",
                                "Status ${response.httpCode}\n${response.body.take(4000)}\n\n${savedPath?.let { "Opgeslagen als: $it" } ?: ""}"
                            )
                        } else {
                            val body = response.body
                            val nok = body.contains("\"NOK\"", ignoreCase = true) || body.contains(">NOK<", ignoreCase = true)
                            if (nok) {
                                showErrorDialog("Server NOK", body.take(4000))
                            } else {
                                val (onlineId, tellingId) = parseIdsFromCountsSaveResponse(body)
                                requireContext().getSharedPreferences(uploadPrefs, Context.MODE_PRIVATE).edit {
                                    onlineId?.let { putString(keyOnlineId, it) }
                                    tellingId?.let { putString(keyTellingId, it) }
                                }
                                val msg = buildString {
                                    append("Telling opgeslagen.")
                                    onlineId?.let { append(" Online ID: $it.") }
                                    tellingId?.let { append(" Telling ID: $it.") }
                                    savedPath?.let { append("\nResp: $it") }
                                }
                                Snackbar.make(bind.root, msg, Snackbar.LENGTH_LONG)
                                    .setAnchorView(bind.bottomBar)
                                    .show()

                                findNavController().navigate(R.id.action_metadataScherm_to_soortSelectieScherm)
                            }
                        }
                    }
                }
            }
        }
    }
    // endregion

    /** Toon 1x login-dialoog als credentials ontbreken, sla op, en voer dan [action] uit. */
    private fun ensureCredentialsThen(action: () -> Unit) {
        val have = CredentialsStore.get(requireContext())
        if (have != null) {
            if (isAdded && _binding != null) action()
            return
        }

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
        }

        fun makeField(hintRes: Int, password: Boolean = false): Pair<TextInputLayout, TextInputEditText> {
            val til = TextInputLayout(requireContext()).apply {
                isHintEnabled = true
                hint = getString(hintRes)
            }
            val et = TextInputEditText(requireContext()).apply {
                if (password) {
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or
                            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
            }
            til.addView(et)
            return til to et
        }

        val (tilUser, etUser) = makeField(R.string.lbl_username, false)
        val (tilPass, etPass) = makeField(R.string.lbl_password, true)
        container.addView(tilUser)
        container.addView(tilPass)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.login_title))
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val u = etUser.text?.toString()?.trim().orEmpty()
                val p = etPass.text?.toString()?.trim().orEmpty()
                if (u.isNotEmpty() && p.isNotEmpty()) {
                    CredentialsStore.save(requireContext(), u, p)
                    if (isAdded && _binding != null) action()
                } else {
                    val b = _binding
                    if (b != null) {
                        Snackbar.make(b.root, getString(R.string.login_missing), Snackbar.LENGTH_LONG)
                            .setAnchorView(b.bottomBar)
                            .show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // --------- Codes downloaden (on-demand) ---------

    private fun promptDownloadCodes() {
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Codes niet gevonden")
            .setMessage("Wil je de meest recente codes online downloaden?\n(Vereist internet)")
            .setPositiveButton("Downloaden") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val useAuth = CredentialsStore.isConfigured(ctx)
                    val result = if (useAuth) {
                        CodesRepository.fetchCodesBasicAuthAndSave(ctx)
                    } else {
                        CodesRepository.fetchCodesFallbackByQueryAndSave(ctx)
                    }

                    withContext(Dispatchers.Main) {
                        val b = _binding ?: return@withContext
                        if (result.ok) {
                            // Na succes index verversen voor de rest van de app
                            lifecycleScope.launch {
                                CodesIndex.ensureLoadedAsync(ctx)
                                Snackbar.make(b.root, "Codes gedownload en geladen.", Snackbar.LENGTH_LONG)
                                    .setAnchorView(b.bottomBar)
                                    .show()
                            }
                        } else {
                            showErrorDialog(
                                "Download mislukt",
                                "Status ${result.httpCode}\n${result.body.take(4000)}"
                            )
                        }
                    }
                }
            }
            .setNegativeButton("Annuleren", null)
            .show()
    }

    // region Form verzamelen & payload bouwen
    private fun collectForm(): MetadataForm {
        val hhmm = binding.etTijd.text?.toString()?.takeIf { it.matches(Regex("\\d{2}:\\d{2}")) } ?: selectedTimeHHmm
        val localTime = LocalTime.parse(hhmm, timeFmt)
        val epochMillis = selectedDate.atTime(localTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val labels = resources.getStringArray(R.array.vt4_telpost_labels)
        val values = resources.getStringArray(R.array.vt4_telpost_values)
        val currentTelpostLabel = binding.acTelpost.text?.toString().orEmpty()
        val telpostIndex = labels.indexOf(currentTelpostLabel).takeIf { it >= 0 } ?: labels.lastIndex
        val currentTelpostCode = values[telpostIndex]

        val tellerNaam = binding.etTellers.text?.toString()?.trim().orEmpty()
        val tellerId = "3533" // voorlopig vast

        val windrichting = binding.acWindrichting.text?.toString()?.trim().orEmpty()
        val temperatuurC = binding.etTemperatuur.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
        val bewolking = binding.acBewolking.text?.toString()?.trim().orEmpty()
        val neerslag = binding.acNeerslag.text?.toString()?.trim().orEmpty()
        val zichtMeters = binding.etZicht.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
        val windkrachtBft = binding.acWindkracht.text?.toString()?.trim().orEmpty()
        val luchtdrukHpa = binding.etLuchtdruk.text?.toString()?.replace(',', '.')?.toDoubleOrNull()

        return MetadataForm(
            typeTelling = binding.acTypeTelling.text?.toString()?.trim().orEmpty(),
            locatieType = "",
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
            zichtKm = zichtMeters,
            windkrachtBft = windkrachtBft,
            luchtdrukHpa = luchtdrukHpa,
            opmerkingen = binding.etOpmerkingen.text?.toString()?.trim().orEmpty()
        )
    }

    private fun buildCountsPayloadFromForm(form: MetadataForm): String {
        val externid = "Android App 1.8.45"
        val uuid = "Trektellen_Android_1.8.45_${UUID.randomUUID()}"
        val uploadTs = uploadTsFmt.format(Instant.now().atZone(ZoneId.systemDefault()))

        val typetellingCode = when (form.typeTelling.lowercase(Locale.getDefault())) {
            "alle soorten" -> "all"
            "zeetrek" -> "sea"
            "ooievaars en roofvogels" -> "raptor"
            "landtrek op kusttelpost" -> "coastalvismig"
            else -> "all"
        }

        val windrichtingCode = labelToWindCode(form.windrichting)

        val neerslagCode = when (form.neerslag.lowercase(Locale.getDefault())) {
            "geen" -> "geen"
            "regen" -> "regen"
            "motregen" -> "motregen"
            "mist" -> "mist"
            "hagel" -> "hagel"
            "sneeuw" -> "sneeuw"
            "sneeuw- of zandstorm", "sneeuw- of zandstorm " -> "sneeuw_zandstorm"
            "onweer" -> "onweer"
            else -> ""
        }

        val bewolkingInt = form.bewolkingAchtsten.substringBefore('/').trim().toIntOrNull() ?: 0

        val windkrachtInt = when {
            form.windkrachtBft.equals("var", true) -> 0
            form.windkrachtBft.startsWith("<1", true) -> 0
            form.windkrachtBft.startsWith(">11", true) -> 12
            form.windkrachtBft.endsWith("bf", true) -> form.windkrachtBft.substringBefore("bf").trim().toIntOrNull() ?: 0
            else -> 0
        }

        val zichtMetersInt = (form.zichtKm ?: 0.0).toInt()
        val tempInt = (form.temperatuurC ?: 0.0).toInt()
        val hpaInt = (form.luchtdrukHpa ?: 0.0).toInt()

        val beginEpochSec = (form.datumEpochMillis / 1000L).toString()
        val eindEpochSec = beginEpochSec

        val obj = JSONObject().apply {
            put("externid", externid)
            put("timezoneid", "Europe/Brussels")
            put("bron", "4")
            put("_id", "")
            put("tellingid", "")
            put("telpostid", form.telpostCode)
            put("begintijd", beginEpochSec)
            put("eindtijd", eindEpochSec)
            put("tellers", form.tellersNaam)
            put("weer", binding.etWeerOpmerking.text?.toString()?.trim().orEmpty())
            put("windrichting", windrichtingCode)
            put("windkracht", windkrachtInt.toString())
            put("temperatuur", tempInt.toString())
            put("bewolking", bewolkingInt.toString())
            put("bewolkinghoogte", "")
            put("neerslag", neerslagCode)
            put("duurneerslag", "")
            put("zicht", zichtMetersInt.toString())
            put("tellersactief", "")
            put("tellersaanwezig", "")
            put("typetelling", typetellingCode)
            put("metersnet", "")
            put("geluid", "")
            put("opmerkingen", form.opmerkingen)
            put("onlineid", "")
            put("HYDRO", "")
            put("hpa", if (hpaInt > 0) hpaInt.toString() else "")
            put("equipment", "")
            put("uuid", uuid)
            put("uploadtijdstip", uploadTs)
            put("nrec", "0")
            put("nsoort", "0")
            put("data", JSONArray())
        }

        val arr = JSONArray().put(obj)
        return arr.toString()
    }

    private fun labelToWindCode(label: String): String {
        val l = label.trim().lowercase(Locale.getDefault())
        return when (l) {
            "n","nno","no","ono","o","ozo","zo","zzo","z","zzw","zw","wzw","w","wnw","nw","nnw","var","variabel" -> {
                if (l == "variabel") "var" else l
            }
            else -> l
        }
    }
    // endregion

    // region Response parsing & UI helpers
    private fun parseIdsFromCountsSaveResponse(body: String): Pair<String?, String?> {
        return try {
            val root = JSONObject(body)
            val arr = root.optJSONArray("json")
            if (arr != null && arr.length() > 0) {
                val first = arr.getJSONObject(0)
                val online = first.optString("onlineid", "").ifBlank { null }
                val telling = first.optString("tellingid", "").ifBlank { null }
                online to telling
            } else {
                val online = JSONObject(body).optString("onlineid", "").ifBlank { null }
                online to null
            }
        } catch (_: Throwable) {
            val online = Regex("\"onlineid\"\\s*:\\s*\"?(\\d+)\"?").find(body)?.groupValues?.getOrNull(1)
            val telling = Regex("\"tellingid\"\\s*:\\s*\"?(\\d+)\"?").find(body)?.groupValues?.getOrNull(1)
            online to telling
        }
    }

    private fun showErrorDialog(title: String, message: String) {
        val tv = TextView(requireContext()).apply {
            text = message
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

    private fun savedPathToHuman(dirPath: String, fileName: String): String = "$dirPath/$fileName"
    // endregion

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
