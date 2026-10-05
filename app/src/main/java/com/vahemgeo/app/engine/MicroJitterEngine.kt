package com.vahemgeo.app.engine

import kotlin.random.Random

object MicroJitterEngine {
    // Approx 1 meter ~ 0.000009 degrees latitude/longitude
    private const val MAX_JITTER_DEGREES = 0.000007

    /**
     * Injects micro sub-meter natural GPS drift into coordinates to bypass static mock detection.
     */
    fun applyJitter(latitude: Double, longitude: Double, enabled: Boolean): Pair<Double, Double> {
        if (!enabled) return Pair(latitude, longitude)

        val latJitter = (Random.nextDouble() - 0.5) * 2 * MAX_JITTER_DEGREES
        val lngJitter = (Random.nextDouble() - 0.5) * 2 * MAX_JITTER_DEGREES

        return Pair(latitude + latJitter, longitude + lngJitter)
    }
}
