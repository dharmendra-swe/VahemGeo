package com.vahemgeo.app.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.util.concurrent.TimeUnit

object OsrmRoutingService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches driving route waypoints along roads between given waypoints using OSRM REST API.
     */
    suspend fun fetchRoute(waypoints: List<GeoPoint>): List<GeoPoint> = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) return@withContext waypoints

        val coordsString = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
        val url = "https://router.project-osrm.org/route/v1/driving/$coordsString?overview=full&geometries=geojson"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "VahemGeo-AndroidApp")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext waypoints

            val jsonBody = response.body?.string() ?: return@withContext waypoints
            val jsonObject = JSONObject(jsonBody)

            val routesArray = jsonObject.optJSONArray("routes")
            if (routesArray == null || routesArray.length() == 0) return@withContext waypoints

            val route = routesArray.getJSONObject(0)
            val geometry = route.getJSONObject("geometry")
            val coordinates = geometry.getJSONArray("coordinates")

            val routePoints = mutableListOf<GeoPoint>()
            for (i in 0 until coordinates.length()) {
                val point = coordinates.getJSONArray(i)
                val lng = point.getDouble(0)
                val lat = point.getDouble(1)
                routePoints.add(GeoPoint(lat, lng))
            }

            if (routePoints.isNotEmpty()) routePoints else waypoints
        } catch (e: Exception) {
            e.printStackTrace()
            waypoints
        }
    }
}
