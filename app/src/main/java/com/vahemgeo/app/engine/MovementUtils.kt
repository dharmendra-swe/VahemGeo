package com.vahemgeo.app.engine

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

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

    /**
     * Calculates initial bearing in degrees (0..360) from point 1 to point 2.
     */
    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLonRad = Math.toRadians(lon2 - lon1)

        val y = sin(dLonRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLonRad)

        var bearing = Math.toDegrees(atan2(y, x)).toFloat()
        if (bearing < 0) bearing += 360f
        return bearing
    }

    /**
     * Smoothly interpolates current bearing towards target bearing to avoid unnatural 90° snaps.
     */
    fun interpolateBearing(currentBearing: Float, targetBearing: Float, maxTurnStepDegrees: Float = 15f): Float {
        var diff = (targetBearing - currentBearing + 180f) % 360f - 180f
        if (diff < -180f) diff += 360f

        val clampedTurn = diff.coerceIn(-maxTurnStepDegrees, maxTurnStepDegrees)
        var newBearing = (currentBearing + clampedTurn) % 360f
        if (newBearing < 0) newBearing += 360f
        return newBearing
    }

    /**
     * Applies subtle natural speed fluctuation (±5% pace variation) to simulate real human/vehicle movement.
     */
    fun applySpeedVariance(baseSpeedKmH: Float): Float {
        if (baseSpeedKmH <= 0f) return 0f
        val variation = (Random.nextFloat() - 0.5f) * 0.08f * baseSpeedKmH
        return (baseSpeedKmH + variation).coerceAtLeast(0.5f)
    }
}

