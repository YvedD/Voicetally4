package com.yvesds.voicetally4.utils.weather

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Haalt "current" weerdata op via Open-Meteo.
 * Geen Google Play Services nodig. Je levert gewoon (lat, lon) aan.
 */
class WeatherManager(private val appContext: Context) {

    companion object {
        private const val TAG = "WeatherManager"
        private const val WEATHER_API = "https://api.open-meteo.com/v1/forecast"
    }

    /**
     * @param lat Latitude (WGS84)
     * @param lon Longitude (WGS84)
     */
    suspend fun fetchCurrentWeather(lat: Double, lon: Double): WeatherResponse? = withContext(Dispatchers.IO) {
        try {
            // Open-Meteo: "current" parameters + eenheden
            val urlStr = buildString {
                append("$WEATHER_API?latitude=$lat&longitude=$lon")
                append("&current=temperature_2m,precipitation,weathercode,")
                append("wind_speed_10m,wind_direction_10m,cloud_cover,visibility,pressure_msl")
                append("&wind_speed_unit=kmh&precipitation_unit=mm&timezone=auto")
            }

            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(response)
            val current = json.getJSONObject("current")

            val locality = reverseGeocodeLocality(lat, lon)

            return@withContext WeatherResponse(
                locationName = locality,
                temperature   = current.optDouble("temperature_2m", Double.NaN),
                precipitation = current.optDouble("precipitation", 0.0),
                weathercode   = current.optInt("weathercode", -1),
                windspeed     = current.optDouble("wind_speed_10m", 0.0),
                winddirection = current.optInt("wind_direction_10m", 0),
                pressure      = current.optDouble("pressure_msl", 0.0).roundToInt(),
                cloudcover    = current.optInt("cloud_cover", 0),
                visibility    = current.optInt("visibility", 0), // meters
                time          = current.optString("time", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij ophalen weer: ${e.message}", e)
            null
        }
    }

    // --- Helpers voor mapping/formattering ---

    /** Beaufortschaal op basis van km/h. Retourneert 0..12. */
    fun toBeaufort(speedKmh: Double): Int = when {
        speedKmh < 1.0   -> 0
        speedKmh < 6.0   -> 1
        speedKmh < 12.0  -> 2
        speedKmh < 20.0  -> 3
        speedKmh < 29.0  -> 4
        speedKmh < 39.0  -> 5
        speedKmh < 50.0  -> 6
        speedKmh < 62.0  -> 7
        speedKmh < 75.0  -> 8
        speedKmh < 89.0  -> 9
        speedKmh < 103.0 -> 10
        speedKmh < 118.0 -> 11
        else             -> 12
    }

    /** 0..360° naar kompasrichting (N, NNO, NO, ...). */
    fun toCompass(degrees: Int): String {
        val dirs = listOf(
            "N", "NNO", "NO", "ONO",
            "O", "OZO", "ZO", "ZZO",
            "Z", "ZZW", "ZW", "WZW",
            "W", "WNW", "NW", "NNW"
        )
        val index = (((degrees / 22.5) + 0.5).toInt()) % 16
        return dirs[index]
    }

    /** Cloudcover (%) → octa (0..8). */
    fun toOctas(percent: Int): Int = (percent / 12.5).roundToInt().coerceIn(0, 8)

    /**
     * Open-Meteo weathercode → 'Neerslag'-spinner opties.
     * Opties: geen - regen - motregen - mist - hagel - sneeuw - sneeuw- of zandstorm - onweer
     */
    fun mapWeatherCodeToNeerslagOption(code: Int): String = when (code) {
        45, 48 -> "mist"
        51, 53, 55 -> "motregen"
        61, 63, 65, 66, 67, 80, 81, 82 -> "regen"
        71, 73, 75, 77, 85, 86 -> "sneeuw"
        96, 99 -> "onweer" // met hagel; jouw lijst heeft aparte 'hagel', maar we kiezen 'onweer'
        95 -> "onweer"
        else -> "geen" // 0..3 zonnig/bewolkt → geen neerslag
    }

    /**
     * Reverse-geocode naar locality/plaatsnaam.
     * - Op Android 13+ (API 33) gebruiken we de niet-verouderde listener-API.
     * - Op oudere versies gebruiken we reflectie om de oude API aan te roepen (geen @Suppress nodig).
     */
    private suspend fun reverseGeocodeLocality(lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(appContext, Locale.getDefault())

                if (Build.VERSION.SDK_INT >= 33) {
                    // Nieuwe, niet-deprecated API met callback
                    return@withContext suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                val name = addresses.firstOrNull()?.locality
                                if (cont.isActive) cont.resume(name)
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                    }
                } else {
                    // Oudere API via reflectie (vermijdt directe referentie → geen deprecation warning)
                    val method = Geocoder::class.java.getMethod(
                        "getFromLocation",
                        Double::class.javaPrimitiveType,
                        Double::class.javaPrimitiveType,
                        Integer.TYPE
                    )
                    val result = method.invoke(geocoder, lat, lon, 1) as? List<*>
                    val addr = result?.firstOrNull() as? Address
                    return@withContext addr?.locality
                }
            } catch (_: Exception) {
                return@withContext null
            }
        }
}
