package com.vahemgeo.app.engine

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock

class MockLocationManager(context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

    fun setupMockProviders() {
        for (provider in providers) {
            try {
                // Remove provider first if it already exists to ensure clean state
                try {
                    locationManager.removeTestProvider(provider)
                } catch (_: Exception) {}

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    locationManager.addTestProvider(
                        provider,
                        false,
                        false,
                        false,
                        false,
                        true,
                        true,
                        true,
                        ProviderProperties.POWER_USAGE_LOW,
                        ProviderProperties.ACCURACY_FINE
                    )
                } else {
                    @Suppress("DEPRECATION")
                    locationManager.addTestProvider(
                        provider,
                        provider == LocationManager.NETWORK_PROVIDER,
                        false,
                        false,
                        false,
                        true,
                        true,
                        true,
                        0,
                        5
                    )
                }
                locationManager.setTestProviderEnabled(provider, true)
            } catch (e: SecurityException) {
                // Thrown if the app is not selected under "Select mock location app" in Developer Options
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun pushLocation(lat: Double, lng: Double, altitude: Double = 15.0, bearing: Float = 0f) {
        val currentTime = System.currentTimeMillis()
        val elapsedRealtime = SystemClock.elapsedRealtimeNanos()

        for (provider in providers) {
            val mockLocation = Location(provider).apply {
                latitude = lat
                longitude = lng
                this.altitude = altitude
                speed = 0.0f
                this.bearing = bearing
                accuracy = 1.0f
                time = currentTime
                elapsedRealtimeNanos = elapsedRealtime
            }

            try {
                locationManager.setTestProviderLocation(provider, mockLocation)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeMockProviders() {
        for (provider in providers) {
            try {
                locationManager.setTestProviderEnabled(provider, false)
                locationManager.removeTestProvider(provider)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
