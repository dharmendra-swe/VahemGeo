package com.vahemgeo.app.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

data class SearchLocationResult(
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double
)

object LocationSearchService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Searches for places by name/address using OpenStreetMap Nominatim API,
     * and also supports direct "lat, lng" coordinate input.
     */
    suspend fun searchLocations(query: String): List<SearchLocationResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        // 1. Check if the user typed direct coordinates (e.g. "28.6139, 77.2090")
        parseCoordinatesOrNull(trimmed)?.let { (lat, lng) ->
            return@withContext listOf(
                SearchLocationResult(
                    title = "Coordinates (${"%.5f".format(Locale.US, lat)}, ${"%.5f".format(Locale.US, lng)})",
                    subtitle = "Direct GPS Coordinates",
                    latitude = lat,
                    longitude = lng
                )
            )
        }

        // 2. Query Nominatim OpenStreetMap API
        try {
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=6&addressdetails=1"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "VahemGeo-Android/1.0 (https://vahemgeo.app)")
                .header("Accept-Language", Locale.getDefault().language.ifBlank { "en" })
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val jsonArray = JSONArray(body)

                val results = mutableListOf<SearchLocationResult>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val lat = obj.optDouble("lat", Double.NaN)
                    val lon = obj.optDouble("lon", Double.NaN)
                    if (lat.isNaN() || lon.isNaN()) continue

                    val displayName = obj.optString("display_name", "Unknown Location")
                    val parts = displayName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val primaryTitle = obj.optString("name", "").ifBlank {
                        parts.firstOrNull() ?: "Location"
                    }
                    val subtitle = if (parts.size > 1) {
                        parts.drop(1).take(3).joinToString(", ")
                    } else {
                        "${"%.5f".format(Locale.US, lat)}, ${"%.5f".format(Locale.US, lon)}"
                    }

                    results.add(
                        SearchLocationResult(
                            title = primaryTitle,
                            subtitle = subtitle,
                            latitude = lat,
                            longitude = lon
                        )
                    )
                }
                results
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Reverse geocodes coordinates into a short human-readable address/place name.
     */
    suspend fun reverseGeocode(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        val fallback = "${"%.4f".format(Locale.US, lat)}, ${"%.4f".format(Locale.US, lng)}"
        try {
            val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lng&format=json&zoom=16"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "VahemGeo-Android/1.0 (https://vahemgeo.app)")
                .header("Accept-Language", Locale.getDefault().language.ifBlank { "en" })
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext fallback
                val body = response.body?.string() ?: return@withContext fallback
                val obj = JSONObject(body)
                val name = obj.optString("name", "")
                val displayName = obj.optString("display_name", "")
                val parts = displayName.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                when {
                    name.isNotBlank() && parts.size > 1 -> "$name, ${parts[1]}"
                    parts.size >= 2 -> "${parts[0]}, ${parts[1]}"
                    parts.size == 1 -> parts[0]
                    else -> fallback
                }
            }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun parseCoordinatesOrNull(input: String): Pair<Double, Double>? {
        val regex = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*[,\s]\s*(-?\d+(?:\.\d+)?)\s*$""")
        val match = regex.matchEntire(input) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lng = match.groupValues[2].toDoubleOrNull() ?: return null
        if (lat in -90.0..90.0 && lng in -180.0..180.0) {
            return Pair(lat, lng)
        }
        return null
    }
}
