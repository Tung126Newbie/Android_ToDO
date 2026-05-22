package com.example.simplenotes.util

import android.content.Context
import android.location.Geocoder
import com.example.simplenotes.data.remote.weather.WeatherApiService
import com.example.simplenotes.domain.model.WeatherData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherService @Inject constructor(
    private val weatherApiService: WeatherApiService,
    @ApplicationContext private val context: Context
) {
    suspend fun fetchWeather(lat: Double, lon: Double): Result<WeatherData> = withContext(Dispatchers.IO) {
        try {
            val response = weatherApiService.getWeatherData(lat, lon)
            if (response.isSuccessful) {
                val body = response.body()
                val current = body?.current
                if (current != null) {
                    Result.success(
                        WeatherData(
                            temperature = current.temperature,
                            weatherCode = current.weatherCode,
                            isDay = current.isDay == 1,
                            time = current.time
                        )
                    )
                } else {
                    Result.failure(Exception("Empty weather response"))
                }
            } else {
                Result.failure(Exception("Weather API error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCoordinatesFromLocationName(locationName: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocationName(locationName, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                Pair(address.latitude, address.longitude)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
