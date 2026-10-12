package com.vahemgeo.app.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.util.concurrent.TimeUnit

data class RouteCalculationResult(
    val points: List<GeoPoint>,
    val totalDistanceMeters: Double,
    val isRoadSnapped: Boolean
)

object OsrmRoutingService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches driving/walking route waypoints along real roads between given waypoints using OSRM REST API.
     * Falls back to smooth great-circle interpolation if offline.
     */
    suspend fun fetchRoute(waypoints: List<GeoPoint>): List<GeoPoint> {
        return calculateDetailedRoute(waypoints).points
    }

    suspend fun calculateDetailedRoute(waypoints: List<GeoPoint>): RouteCalculationResult = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) {
            return@withContext RouteCalculationResult(waypoints, 0.0, false)
        }

        val coordsString = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
        val url = "https://router.project-osrm.org/route/v1/driving/$coordsString?overview=full&geometries=geojson"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "VahemGeo-AndroidApp/1.0")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonBody = response.body?.string()
                    if (!jsonBody.isNullOrBlank()) {
                        val jsonObject = JSONObject(jsonBody)
                        val routesArray = jsonObject.optJSONArray("routes")
                        if (routesArray != null && routesArray.length() > 0) {
                            val route = routesArray.getJSONObject(0)
                            val distanceMeters = route.optDouble("distance", 0.0)
                            val geometry = route.getJSONObject("geometry")
                            val coordinates = geometry.getJSONArray("coordinates")

                            val routePoints = mutableListOf<GeoPoint>()
                            for (i in 0 until coordinates.length()) {
                                val point = coordinates.getJSONArray(i)
                                val lng = point.getDouble(0)
                                val lat = point.getDouble(1)
                                routePoints.add(GeoPoint(lat, lng))
                            }

                            if (routePoints.size >= 2) {
                                val computedDist = if (distanceMeters > 0.0) distanceMeters else computeTotalDistanceMeters(routePoints)
                                return@withContext RouteCalculationResult(
                                    points = routePoints,
                                    totalDistanceMeters = computedDist,
                                    isRoadSnapped = true
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Offline / Fallback: generate smooth interpolated path between waypoints
        val interpolated = interpolateWaypoints(waypoints, maxStepMeters = 25.0)
        RouteCalculationResult(
            points = interpolated,
            totalDistanceMeters = computeTotalDistanceMeters(interpolated),
            isRoadSnapped = false
        )
    }

    fun computeTotalDistanceMeters(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += points[i].distanceToAsDouble(points[i + 1])
        }
        return total
    }

    private fun interpolateWaypoints(waypoints: List<GeoPoint>, maxStepMeters: Double): List<GeoPoint> {
        if (waypoints.size < 2) return waypoints
        val result = mutableListOf<GeoPoint>()
        for (i in 0 until waypoints.size - 1) {
            val start = waypoints[i]
            val end = waypoints[i + 1]
            result.add(start)
            val dist = start.distanceToAsDouble(end)
            if (dist > maxStepMeters) {
                val steps = (dist / maxStepMeters).toInt().coerceIn(1, 500)
                for (step in 1 until steps) {
                    val fraction = step.toDouble() / steps.toDouble()
                    val lat = start.latitude + (end.latitude - start.latitude) * fraction
                    val lng = start.longitude + (end.longitude - start.longitude) * fraction
                    result.add(GeoPoint(lat, lng))
                }
            }
        }
        result.add(waypoints.last())
        return result
    }
}
