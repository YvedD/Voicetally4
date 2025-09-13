package com.yvesds.voicetally4.utils.weather

/**
 * Eenduidig model voor "current" weerdata.
 * Eenheden:
 * - temperature: °C
 * - windspeed: km/h
 * - winddirection: graden (0..360)
 * - precipitation: mm
 * - pressure: hPa
 * - cloudcover: %
 * - visibility: meter
 * - weathercode: Open-Meteo code
 * - time: ISO-8601 (lokale tijd, want timezone=auto)
 */
data class WeatherResponse(
    val locationName: String?,
    val temperature: Double,
    val windspeed: Double,
    val winddirection: Int,
    val precipitation: Double,
    val pressure: Int,
    val cloudcover: Int,
    val visibility: Int,
    val weathercode: Int,
    val time: String
)
