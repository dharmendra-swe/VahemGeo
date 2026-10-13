package com.vahemgeo.app.engine

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlin.random.Random

class MockLocationManager(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fusedClient: FusedLocationProviderClient? = runCatching {
        LocationServices.getFusedLocationProviderClient(appContext)
    }.getOrNull()

    private val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.FUSED_PROVIDER
    )

    // Smooth altitude drift state so elevation never jumps robotically
    private var smoothAltitude = 215.0

    @SuppressLint("MissingPermission")
    fun setupMockProviders() {
        var anyProviderSucceeded = false
        var lastSecurityError: SecurityException? = null

        for (provider in providers) {
            try {
                // Remove provider first if it already exists to ensure clean state
                try {
                    locationManager.removeTestProvider(provider)
                } catch (_: Exception) {}

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val hasSat = (provider == LocationManager.GPS_PROVIDER)
                    val hasNet = (provider != LocationManager.GPS_PROVIDER)
                    val powerUsage = if (provider == LocationManager.GPS_PROVIDER) {
                        ProviderProperties.POWER_USAGE_HIGH
                    } else {
                        ProviderProperties.POWER_USAGE_LOW
                    }

                    locationManager.addTestProvider(
                        provider,
                        hasNet, // requiresNetwork
                        hasSat, // requiresSatellite
                        hasNet, // requiresCell
                        false,  // hasMonetaryCost
                        true,   // supportsAltitude
                        true,   // supportsSpeed
                        true,   // supportsBearing
                        powerUsage,
                        ProviderProperties.ACCURACY_FINE
                    )
                } else {
                    @Suppress("DEPRECATION")
                    locationManager.addTestProvider(
                        provider,
                        provider == LocationManager.NETWORK_PROVIDER, // requiresNetwork
                        provider == LocationManager.GPS_PROVIDER,     // requiresSatellite
                        provider == LocationManager.NETWORK_PROVIDER, // requiresCell
                        false, // hasMonetaryCost
                        true,  // supportsAltitude
                        true,  // supportsSpeed
                        true,  // supportsBearing
                        1,     // powerRequirement
                        1      // accuracy (ACCURACY_FINE)
                    )
                }
                locationManager.setTestProviderEnabled(provider, true)
                anyProviderSucceeded = true
            } catch (e: SecurityException) {
                lastSecurityError = e
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!anyProviderSucceeded && lastSecurityError != null) {
            throw lastSecurityError
        }

        // Also enable Mock Mode in Google Play Services FusedLocationProviderClient (prevents FLP real-location flicker)
        try {
            fusedClient?.setMockMode(true)
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    fun pushLocation(
        lat: Double,
        lng: Double,
        altitude: Double = 215.0,
        bearing: Float = 0f,
        speed: Float = 0f,
        isJitterActive: Boolean = true
    ) {
        val baseTime = System.currentTimeMillis()
        val baseNanos = SystemClock.elapsedRealtimeNanos()

        // Smooth barometric altitude random walk (±0.15m per step, clamped near base altitude)
        val altStep = (Random.nextDouble() - 0.5) * 0.25
        smoothAltitude = (smoothAltitude + altStep).coerceIn(altitude - 3.5, altitude + 3.5)

        // Coherent speed: if stationary but micro-jitter is active, report subtle drift speed (0.04..0.15 m/s)
        val coherentSpeed = if (speed <= 0.01f && isJitterActive) {
            0.04f + Random.nextFloat() * 0.11f
        } else {
            speed
        }

        // Dynamic realistic accuracy variance (GPS tighter than Network)
        val baseAccuracy = 2.8f + (Random.nextFloat() * 1.8f)

        var gmsLocationCandidate: Location? = null

        providers.forEachIndexed { index, provider ->
            val timeOffset = index * 2L // Stagger by 2ms to prevent system location deduplication
            val nanosOffset = index * 2_000_000L
            val providerAccuracy = if (provider == LocationManager.NETWORK_PROVIDER) {
                baseAccuracy + 3.5f
            } else {
                baseAccuracy
            }

            val mockLocation = Location(provider).apply {
                latitude = lat
                longitude = lng
                this.altitude = smoothAltitude
                this.speed = coherentSpeed
                this.bearing = bearing
                accuracy = providerAccuracy
                time = baseTime + timeOffset
                elapsedRealtimeNanos = baseNanos + nanosOffset

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    bearingAccuracyDegrees = 1.5f + (Random.nextFloat() * 2.0f)
                    speedAccuracyMetersPerSecond = 0.15f + (Random.nextFloat() * 0.25f)
                    verticalAccuracyMeters = 1.2f + (Random.nextFloat() * 1.3f)
                }

                // Inject realistic satellite and network extras bundle
                val bundle = Bundle()
                if (provider == LocationManager.GPS_PROVIDER) {
                    bundle.putInt("satellites", 15 + Random.nextInt(5))
                    bundle.putInt("maxSatellites", 28)
                    bundle.putInt("meanCn0", 36 + Random.nextInt(4))
                } else if (provider == LocationManager.NETWORK_PROVIDER) {
                    bundle.putString("networkLocationType", "wifi")
                }
                extras = bundle
            }

            attemptClearMockFlag(mockLocation)

            if (provider == LocationManager.GPS_PROVIDER) {
                gmsLocationCandidate = mockLocation
            }

            try {
                locationManager.setTestProviderLocation(provider, mockLocation)
            } catch (_: Exception) {
            }
        }

        // Push directly into Google Play Services FusedLocationProviderClient as well
        gmsLocationCandidate?.let { flpLoc ->
            try {
                fusedClient?.setMockLocation(flpLoc)
            } catch (_: Exception) {
            }
        }
    }

    private fun attemptClearMockFlag(location: Location) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                location.isMock = false
            }
        } catch (_: Throwable) {}

        try {
            val method = Location::class.java.getDeclaredMethod("setIsFromMockProvider", Boolean::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(location, false)
        } catch (_: Throwable) {}
    }

    @SuppressLint("MissingPermission")
    fun removeMockProviders() {
        try {
            fusedClient?.setMockMode(false)
        } catch (_: Exception) {
        }

        for (provider in providers) {
            try {
                locationManager.setTestProviderEnabled(provider, false)
                locationManager.removeTestProvider(provider)
            } catch (_: Exception) {
            }
        }
    }
}
