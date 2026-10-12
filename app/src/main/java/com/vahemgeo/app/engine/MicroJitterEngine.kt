package com.vahemgeo.app.engine

import kotlin.random.Random

object MicroJitterEngine {
    // Approx 1 meter ~ 0.000009 degrees latitude/longitude
    private const val MAX_JITTER_DEGREES = 0.000004

    @Volatile
    private var lastDriftLat = 0.0
    @Volatile
    private var lastDriftLng = 0.0

    /**
     * Injects realistic micro sub-meter natural GPS drift into coordinates.
     * Uses smoothed random-walk noise to prevent robotic static detection while keeping movement smooth.
     */
    fun applyJitter(latitude: Double, longitude: Double, enabled: Boolean): Pair<Double, Double> {
        if (!enabled) return Pair(latitude, longitude)

        // Smooth Brownian random walk step
        val stepLat = (Random.nextDouble() - 0.5) * 0.0000015
        val stepLng = (Random.nextDouble() - 0.5) * 0.0000015

        // Clamp drift within realistic sub-meter radius
        lastDriftLat = (lastDriftLat + stepLat).coerceIn(-MAX_JITTER_DEGREES, MAX_JITTER_DEGREES)
        lastDriftLng = (lastDriftLng + stepLng).coerceIn(-MAX_JITTER_DEGREES, MAX_JITTER_DEGREES)

        return Pair(latitude + lastDriftLat, longitude + lastDriftLng)
    }
}

