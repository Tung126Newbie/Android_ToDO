package com.example.simplenotes.domain.model

data class WeatherData(
    val temperature: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val time: String? = null,
    val locationName: String? = null
) {
    fun getWeatherDescription(isVietnamese: Boolean): String {
        return when (weatherCode) {
            0 -> if (isVietnamese) "Trời quang" else "Clear sky"
            1, 2, 3 -> if (isVietnamese) "Ít mây" else "Mainly clear"
            45, 48 -> if (isVietnamese) "Sương mù" else "Fog"
            51, 53, 55 -> if (isVietnamese) "Mưa phùn" else "Drizzle"
            61, 63, 65 -> if (isVietnamese) "Mưa" else "Rain"
            71, 73, 75 -> if (isVietnamese) "Tuyết rơi" else "Snow fall"
            77 -> if (isVietnamese) "Mưa đá" else "Snow grains"
            80, 81, 82 -> if (isVietnamese) "Mưa rào" else "Rain showers"
            85, 86 -> if (isVietnamese) "Tuyết rào" else "Snow showers"
            95, 96, 99 -> if (isVietnamese) "Giông bão" else "Thunderstorm"
            else -> if (isVietnamese) "Không xác định" else "Unknown"
        }
    }
}
