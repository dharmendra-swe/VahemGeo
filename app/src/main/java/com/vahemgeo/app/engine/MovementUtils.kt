package com.vahemgeo.app.engine

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object MovementUtils {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates the next (latitude, longitude) given starting coordinates,
     * distance moved in meters, and bearing in degrees (0 = North, 90 = East, 180 = South, 270 = West).
     */
    fun calculateNextCoordinates(
        currentLat: Double,
        currentLng: Double,
        distanceMeters: Double,
        bearingDegrees: Float
    ): Pair<Double, Double> {
        if (distanceMeters <= 0) return Pair(currentLat, currentLng)

        val latRad = Math.toRadians(currentLat)
        val lngRad = Math.toRadians(currentLng)
        val bearingRad = Math.toRadians(bearingDegrees.toDouble())

        val angularDistance = distanceMeters / EARTH_RADIUS_METERS

        val nextLatRad = asin(
            sin(latRad) * cos(angularDistance) +
                    cos(latRad) * sin(angularDistance) * cos(bearingRad)
        )

        val nextLngRad = lngRad + atan2(
            sin(bearingRad) * sin(angularDistance) * cos(latRad),
            cos(angularDistance) - sin(latRad) * sin(nextLatRad)
        )

        val nextLat = Math.toDegrees(nextLatRad)
        // Normalize longitude to -180..+180
        val nextLng = (Math.toDegrees(nextLngRad) + 540) % 360 - 180

        return Pair(nextLat, nextLng)
    }

    /**
     * Calculates distance in meters per update interval based on speed in km/h.
     * @param speedKmH Speed in km/h
     * @param intervalMs Update interval in milliseconds
     */
    fun speedToMetersPerInterval(speedKmH: Float, intervalMs: Long): Double {
        val speedMetersPerSecond = speedKmH / 3.6
        return speedMetersPerSecond * (intervalMs / 1000.0)
    }
}
