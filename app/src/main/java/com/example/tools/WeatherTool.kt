package com.example.tools

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WeatherTool : Tool {
    override val name = "WeatherTool"
    override val description = "Fetches live weather reports and forecasts for any city or current location."
    override val parameters = listOf(
        ToolParameter(name = "city", type = "string", description = "The city name (e.g. Delhi, London, New York, Tokyo)", required = false)
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val cityQuery = params["city"]?.toString()?.trim() ?: "Delhi"

        try {
            // Step 1: Geocode city using Open-Meteo Geocoding
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=" +
                    java.net.URLEncoder.encode(cityQuery, "UTF-8") + "&count=1&language=en&format=json"

            val geoReq = Request.Builder().url(geoUrl).build()
            val geoRes = client.newCall(geoReq).execute()
            val geoBody = geoRes.body?.string() ?: ""

            if (!geoRes.isSuccessful || geoBody.isBlank()) {
                return@withContext ToolResult.error("Unable to contact weather geocoding service.")
            }

            val geoJson = JSONObject(geoBody)
            val results = geoJson.optJSONArray("results")
            if (results == null || results.length() == 0) {
                return@withContext ToolResult.error("Could not find location coordinates for \"$cityQuery\".")
            }

            val locationObj = results.getJSONObject(0)
            val resolvedName = locationObj.optString("name", cityQuery)
            val country = locationObj.optString("country", "")
            val lat = locationObj.getDouble("latitude")
            val lon = locationObj.getDouble("longitude")

            // Step 2: Fetch current weather & precipitation probability
            val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current_weather=true&hourly=precipitation_probability&timezone=auto"

            val weatherReq = Request.Builder().url(weatherUrl).build()
            val weatherRes = client.newCall(weatherReq).execute()
            val weatherBody = weatherRes.body?.string() ?: ""

            if (!weatherRes.isSuccessful || weatherBody.isBlank()) {
                return@withContext ToolResult.error("Unable to retrieve weather forecast.")
            }

            val weatherJson = JSONObject(weatherBody)
            val current = weatherJson.getJSONObject("current_weather")
            val temp = current.getDouble("temperature")
            val wind = current.getDouble("windspeed")
            val weatherCode = current.getInt("weathercode")

            // Check hourly rain probability
            var maxRainProb = 0
            val hourly = weatherJson.optJSONObject("hourly")
            val precipProbList = hourly?.optJSONArray("precipitation_probability")
            if (precipProbList != null && precipProbList.length() > 0) {
                for (i in 0 until minOf(precipProbList.length(), 12)) {
                    val p = precipProbList.optInt(i, 0)
                    if (p > maxRainProb) maxRainProb = p
                }
            }

            val condition = interpretWmoCode(weatherCode)
            val rainInfo = if (maxRainProb > 30) " High chance of rain ($maxRainProb%)." else " Rain is unlikely ($maxRainProb%)."
            val locTitle = if (country.isNotBlank()) "$resolvedName, $country" else resolvedName

            val message = "The weather in $locTitle is currently ${temp}°C with $condition. Wind speed is $wind km/h.$rainInfo"

            ToolResult.ok(
                message = message,
                data = mapOf(
                    "location" to locTitle,
                    "temperature" to temp,
                    "condition" to condition,
                    "windSpeed" to wind,
                    "rainProbability" to maxRainProb
                )
            )
        } catch (e: Exception) {
            ToolResult.error("Failed to fetch weather for $cityQuery: ${e.localizedMessage}")
        }
    }

    private fun interpretWmoCode(code: Int): String {
        return when (code) {
            0 -> "clear skies"
            1, 2, 3 -> "partly cloudy"
            45, 48 -> "foggy"
            51, 53, 55 -> "drizzle"
            61, 63, 65 -> "rain"
            71, 73, 75 -> "snowfall"
            80, 81, 82 -> "rain showers"
            95, 96, 99 -> "thunderstorms"
            else -> "moderate conditions"
        }
    }
}
