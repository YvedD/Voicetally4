package com.yvesds.voicetally4.ui.schermen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
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
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.yvesds.voicetally4.utils.io.CodesJsonReader
import com.yvesds.voicetally4.utils.io.StorageUtils
import com.yvesds.voicetally4.utils.net.CredentialsStore
import com.yvesds.voicetally4.utils.net.TrektellenApi
import com.yvesds.voicetally4.utils.upload.CountsPayloadBuilder
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

/**
 * MetadataScherm:
 * - JSON-first spinner-waarden via CodesJsonReader voor typetelling_trek / wind / neerslag.
 * - Vrije telling: telpostCode == "0" → géén upload (bestaande logica blijft).
 * - Overige gedrag ongewijzigd.
 */
class MetadataScherm : Fragment() {

    private var _binding: FragmentMetadataSchermBinding? = null
    private val binding get() = _binding!!

    private lateinit var weatherManager: WeatherManager

    // Tijd
    private var selectedDate: LocalDate = LocalDate.now()
    private var selectedTimeHHmm: String =
        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    private var screenEnterEpochSec: Long = 0L

    // Prefs
    private val prefsName = "metadata_prefs"
    private val keyLastTelpostCode = "last_telpost_code"

    // Upload prefs
    private val uploadPrefs = "upload_prefs"
    private val keyOnlineId = "last_onlineid"
    private val keyTellingId = "last_tellingid"

    // Formatters
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    // Perm launcher
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

        screenEnterEpochSec = Instant.now().epochSecond

        applySystemBarInsets()
        setupDateField()
        setupTimePickerDialog()
        setupTelpostSpinner()
        setupTellersField()

        // JSON-first laad + UI vullen
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) { CodesJsonReader.load(requireContext()) }
            setupWeatherSpinnersJsonFirst()
            hookWeatherButton()
            setupTypeTellingSpinnerJsonFirst()
        }

        setupBottomBar()
    }

    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootConstraint) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            val lp = binding.bottomBar.layoutParams
            if (lp is ViewGroup.MarginLayoutParams) {
                lp.bottomMargin = sys.bottom
                binding.bottomBar.layoutParams = lp
            } else {
                val p = binding.bottomBar.paddingBottom
                binding.bottomBar.setPadding(
                    binding.bottomBar.paddingLeft,
                    binding.bottomBar.paddingTop,
                    binding.bottomBar.paddingRight,
                    p + sys.bottom
                )
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
                _binding?.etDatum?.setText(selectedDate.format(dateFmt))
            }
            picker.show(parentFragmentManager, "datePicker")
        }

        binding.etDatum.setOnClickListener(openDatePicker)
    }
    // endregion

    // region Tijd (custom dialog)
    private fun setupTimePickerDialog() {
        binding.etTijd.setText(selectedTimeHHmm)
        binding.etTijd.setOnClickListener { openTimeSpinnerDialog() }
    }

    private fun openTimeSpinnerDialog() {
        val accentBlue = ContextCompat.getColor(requireContext(), R.color.vt4_outline)
        val current = try {
            LocalTime.parse(selectedTimeHHmm, timeFmt)
        } catch (_: Exception) {
            LocalTime.now()
        }

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.vt4_black))
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        val lblHour = TextView(requireContext()).apply {
            text = "Uren"
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 8)
        }
        val lblMinute = TextView(requireContext()).apply {
            text = "Minuten"
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 8)
        }

        val hourCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 16, 0)
        }
        val hourPicker = NumberPicker(requireContext()).apply {
            minValue = 0; maxValue = 23
            value = current.hour
            wrapSelectorWheel = true
            setFormatter { String.format(Locale.getDefault(), "%02d", it) }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        hourCol.addView(lblHour)
        hourCol.addView(hourPicker)

        val minuteCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 0, 0, 0)
        }
        val minutePicker = NumberPicker(requireContext()).apply {
            minValue = 0; maxValue = 59
            value = current.minute
            wrapSelectorWheel = true
            setFormatter { String.format(Locale.getDefault(), "%02d", it) }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        minuteCol.addView(lblMinute)
        minuteCol.addView(minutePicker)

        minutePicker.setOnValueChangedListener { _, oldVal, newVal ->
            if (oldVal == 59 && newVal == 0) {
                hourPicker.value = (hourPicker.value + 1) % 24
            } else if (oldVal == 0 && newVal == 59) {
                hourPicker.value = (hourPicker.value + 23) % 24
            }
        }

        container.addView(hourCol)
        container.addView(minuteCol)

        val dlg = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.meta_tijd))
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                selectedTimeHHmm =
                    String.format(Locale.getDefault(), "%02d:%02d", hourPicker.value, minutePicker.value)
                _binding?.etTijd?.setText(selectedTimeHHmm)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dlg.setOnShowListener {
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(accentBlue)
            dlg.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(accentBlue)
            dlg.findViewById<TextView>(com.google.android.material.R.id.alertTitle)?.setTextColor(accentBlue)
            setNumberPickerTextColor(hourPicker, Color.WHITE)
            setNumberPickerTextColor(minutePicker, Color.WHITE)
            lblHour.setTextColor(Color.WHITE)
            lblMinute.setTextColor(Color.WHITE)
        }
        dlg.show()
    }

    private fun setNumberPickerTextColor(picker: NumberPicker, color: Int) {
        for (i in 0 until picker.childCount) {
            val child = picker.getChildAt(i)
            if (child is TextView) {
                try {
                    child.setTextColor(color)
                    child.paint.isFakeBoldText = true
                    child.invalidate()
                } catch (_: Exception) { }
            }
        }
        try {
            val id = Resources.getSystem().getIdentifier("numberpicker_input", "id", "android")
            val edit = if (id != 0) picker.findViewById<TextView>(id) else null
            edit?.let {
                it.setTextColor(color)
                it.paint.isFakeBoldText = true
                it.invalidate()
            }
        } catch (_: Exception) { }
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
    }
    // endregion

    // region Weer (JSON-first voor wind & neerslag)
    private fun setupWeatherSpinnersJsonFirst() {
        val windLabels = CodesJsonReader.getLabels("wind")
        val windAdapter = if (windLabels.isNotEmpty())
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, windLabels)
        else
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                resources.getStringArray(R.array.vt4_windrichting_opties)
            )
        binding.acWindrichting.setAdapter(windAdapter)

        binding.acBewolking.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                resources.getStringArray(R.array.vt4_bewolking_achtsten)
            )
        )

        val rainLabels = CodesJsonReader.getLabels("neerslag")
        val rainAdapter = if (rainLabels.isNotEmpty())
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, rainLabels)
        else
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                resources.getStringArray(R.array.vt4_neerslag_opties)
            )
        binding.acNeerslag.setAdapter(rainAdapter)

        binding.acWindkracht.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                resources.getStringArray(R.array.vt4_windkracht_beaufort)
            )
        )
    }

    private fun hookWeatherButton() {
        binding.btnWeerIcon.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            val fine = ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.ACCESS_COARSE_LOCATION
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

    // region Type telling (JSON-first)
    private fun setupTypeTellingSpinnerJsonFirst() {
        val labels = CodesJsonReader.getLabels("typetelling_trek")
        val adapter = if (labels.isNotEmpty())
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
        else
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                resources.getStringArray(R.array.vt4_type_telling_opties)
            )

        binding.acTypeTelling.setAdapter(adapter)

        val defaultLabel = if (labels.isNotEmpty()) {
            labels.firstOrNull { it.equals("alle soorten", ignoreCase = true) } ?: labels.first()
        } else {
            "alle soorten"
        }
        binding.acTypeTelling.setText(defaultLabel, false)
    }
    // endregion

    // region Onderaan (upload)
    private fun setupBottomBar() {
        binding.btnAnnuleer.setOnClickListener { findNavController().popBackStack() }
        binding.btnVerder.setOnClickListener {
            ensureCredentialsThen {
                val b = _binding ?: return@ensureCredentialsThen
                val form = collectForm()

                lifecycleScope.launch(Dispatchers.IO) {
                    // Forceer: eind == begin (epoch seconds op basis van de gekozen tijd)
                    val beginSec = (form.datumEpochMillis / 1000L)
                    val eindSec = beginSec

                    // 1) DEBUG HEADER (pretty) voor log én bestand
                    val debugHeader = CountsPayloadBuilder.buildStartPretty(
                        form = form,
                        beginSec = beginSec,
                        eindSec = eindSec,
                        weerOpmerking = b.etWeerOpmerking.text?.toString()?.trim().orEmpty(),
                        telpostId = form.telpostCode
                    )
                    Log.d("CountsUpload", "HEADER:\n$debugHeader")

                    // SAVE DEBUG HEADER → serverdata
                    val headerPath = try {
                        StorageUtils.saveStringToPublicDocuments(
                            context = requireContext(),
                            subDir = "serverdata",
                            fileName = "counts_save_header_debug.json",
                            content = debugHeader
                        )
                        "${StorageUtils.getPublicAppDir(requireContext(), "serverdata").absolutePath}/counts_save_header_debug.json"
                    } catch (_: Throwable) {
                        null
                    }
                    if (headerPath != null) {
                        Log.d("CountsUpload", "Header opgeslagen als: $headerPath")
                    }

                    // 2) Compacte payload voor de POST
                    val payload = CountsPayloadBuilder.buildStart(
                        form = form,
                        beginSec = beginSec,
                        eindSec = eindSec,
                        weerOpmerking = b.etWeerOpmerking.text?.toString()?.trim().orEmpty(),
                        telpostId = form.telpostCode
                    )

                    val response = TrektellenApi.postCountsSaveBasicAuthAsync(requireContext(), payload)

                    val savedPath = try {
                        StorageUtils.saveStringToPublicDocuments(
                            context = requireContext(),
                            subDir = "serverdata",
                            fileName = "counts_save_last.json",
                            content = response.body
                        )
                        "${StorageUtils.getPublicAppDir(requireContext(), "serverdata").absolutePath}/counts_save_last.json"
                    } catch (_: Throwable) {
                        null
                    }

                    withContext(Dispatchers.Main) {
                        val bind = _binding ?: return@withContext
                        if (!response.ok) {
                            showErrorDialog(
                                "Upload mislukt",
                                "Status ${response.httpCode}\n${response.body.take(4000)}\n\n${
                                    savedPath?.let { "Opgeslagen als: $it" } ?: ""
                                }"
                            )
                        } else {
                            // Nieuw: toon "Telling OK ! : <onlineid>" bij OK/message
                            val (ok, onlineId, tellingId) = parseOkAndIds(response.body)
                            if (ok && onlineId != null) {
                                requireContext().getSharedPreferences(uploadPrefs, Context.MODE_PRIVATE).edit {
                                    putString(keyOnlineId, onlineId)
                                    tellingId?.let { putString(keyTellingId, it) }
                                }
                                Snackbar.make(bind.root, "Telling OK ! : $onlineId", Snackbar.LENGTH_LONG)
                                    .setAnchorView(bind.bottomBar)
                                    .show()
                                findNavController().navigate(R.id.action_metadataScherm_to_soortSelectieScherm)
                            } else {
                                val (online, telling) = parseIdsFromCountsSaveResponse(response.body)
                                requireContext().getSharedPreferences(uploadPrefs, Context.MODE_PRIVATE).edit {
                                    online?.let { putString(keyOnlineId, it) }
                                    telling?.let { putString(keyTellingId, it) }
                                }
                                val msg = if (online != null) "Telling OK ! : $online"
                                else getString(R.string.telling_started_generic)
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
    // endregion

    // region Form verzamelen
    private fun collectForm(): MetadataForm {
        val hhmm = binding.etTijd.text?.toString()?.takeIf { it.matches(Regex("\\d{2}:\\d{2}")) } ?: selectedTimeHHmm
        val localTime = LocalTime.parse(hhmm, timeFmt)
        val epochMillis =
            selectedDate.atTime(localTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

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
    // endregion

    // region Response parsing & dialogs
    /**
     * Herkent expliciet de OK-structuur:
     * {"json":[{"message":"OK","gelukt_array":[{"id":"","onlineid":3176863}], ...}]}
     * Retourneert Triple<ok, onlineId, tellingId>.
     */
    private fun parseOkAndIds(body: String): Triple<Boolean, String?, String?> {
        return try {
            val root = JSONObject(body)
            val arr = root.optJSONArray("json") ?: return Triple(false, null, null)
            if (arr.length() == 0) return Triple(false, null, null)
            val first = arr.getJSONObject(0)
            val msg = first.optString("message", "")
            val ok = msg.equals("OK", ignoreCase = true)
            var onlineId: String? = null
            var tellingId: String? = null
            val geluktArr: JSONArray? = first.optJSONArray("gelukt_array")
            if (geluktArr != null && geluktArr.length() > 0) {
                val firstOk = geluktArr.getJSONObject(0)
                onlineId = firstOk.optString("onlineid", "").ifBlank { null }
                tellingId = firstOk.optString("tellingid", "").ifBlank { null }
            } else {
                onlineId = first.optString("onlineid", "").ifBlank { null }
                tellingId = first.optString("tellingid", "").ifBlank { null }
            }
            Triple(ok, onlineId, tellingId)
        } catch (_: Throwable) {
            Triple(false, null, null)
        }
    }

    private fun parseIdsFromCountsSaveResponse(body: String): Pair<String?, String?> {
        return try {
            val root = JSONObject(body)
            val arr = root.optJSONArray("json")
            if (arr != null && arr.length() > 0) {
                val first = arr.getJSONObject(0)
                val geluktArr: JSONArray? = first.optJSONArray("gelukt_array")
                if (geluktArr != null && geluktArr.length() > 0) {
                    val firstOk = geluktArr.getJSONObject(0)
                    val online = firstOk.optString("onlineid", "").ifBlank { null }
                    val telling = firstOk.optString("tellingid", "").ifBlank { null }
                    online to telling
                } else {
                    val online = first.optString("onlineid", "").ifBlank { null }
                    val telling = first.optString("tellingid", "").ifBlank { null }
                    online to telling
                }
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
    // endregion

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
