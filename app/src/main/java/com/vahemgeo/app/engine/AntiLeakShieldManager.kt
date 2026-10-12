package com.vahemgeo.app.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.osmdroid.util.GeoPoint
import kotlin.coroutines.resume

data class SecurityAuditStatus(
    val safetyScorePercent: Int,
    val isWifiScanningOff: Boolean,
    val isBtScanningOff: Boolean,
    val isVpnActive: Boolean,
    val isCoordinateLockEnabled: Boolean,
    val isWakeLockActive: Boolean,
    val isJitterActive: Boolean
)

object AntiLeakShieldManager {
    private const val PREFS_NAME = "vahemgeo_stealth_prefs"
    private const val KEY_LAST_SPOOF_LAT = "last_spoof_lat"
    private const val KEY_LAST_SPOOF_LNG = "last_spoof_lng"
    private const val KEY_HAS_SPOOF_SAVED = "has_spoof_saved"
    private const val KEY_COORDINATE_LOCK = "coordinate_lock_enabled"
    private const val KEY_LAST_REAL_LAT = "last_real_lat"
    private const val KEY_LAST_REAL_LNG = "last_real_lng"
    private const val KEY_HAS_REAL_SAVED = "has_real_saved"

    // Default fallback (New Delhi, India) only if GPS is off and no history exists
    val DEFAULT_FALLBACK_POINT = GeoPoint(28.6139, 77.2090)

    fun saveSpoofedLocation(context: Context, lat: Double, lng: Double) {
        if (lat == 0.0 && lng == 0.0) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_SPOOF_LAT, lat.toString())
            .putString(KEY_LAST_SPOOF_LNG, lng.toString())
            .putBoolean(KEY_HAS_SPOOF_SAVED, true)
            .apply()
    }

    fun getLastSpoofedLocation(context: Context): GeoPoint? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_HAS_SPOOF_SAVED, false)) return null
        val lat = prefs.getString(KEY_LAST_SPOOF_LAT, null)?.toDoubleOrNull() ?: return null
        val lng = prefs.getString(KEY_LAST_SPOOF_LNG, null)?.toDoubleOrNull() ?: return null
        return GeoPoint(lat, lng)
    }

    fun saveRealLocation(context: Context, lat: Double, lng: Double) {
        if (lat == 0.0 && lng == 0.0) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_REAL_LAT, lat.toString())
            .putString(KEY_LAST_REAL_LNG, lng.toString())
            .putBoolean(KEY_HAS_REAL_SAVED, true)
            .apply()
    }

    fun getSavedRealLocation(context: Context): GeoPoint? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_HAS_REAL_SAVED, false)) return null
        val lat = prefs.getString(KEY_LAST_REAL_LAT, null)?.toDoubleOrNull() ?: return null
        val lng = prefs.getString(KEY_LAST_REAL_LNG, null)?.toDoubleOrNull() ?: return null
        return GeoPoint(lat, lng)
    }

    fun isCoordinateLockEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_COORDINATE_LOCK, true)
    }

    fun setCoordinateLockEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_COORDINATE_LOCK, enabled)
            .apply()
    }

    /**
     * Fetches the device's current GPS/Network location safely.
     * - First checks last known location across all active providers.
     * - If none or stale, requests a live single fix with a 4-second timeout.
     */
    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentDeviceLocation(
        context: Context,
        isSpoofingActive: Boolean
    ): GeoPoint? {
        // If spoofing is currently running, return last spoofed location
        if (isSpoofingActive) {
            getLastSpoofedLocation(context)?.let { return it }
        }

        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            return getSavedRealLocation(context) ?: getLastSpoofedLocation(context)
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return getSavedRealLocation(context)

        val candidateProviders = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.FUSED_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var bestLocation: Location? = null
        for (provider in candidateProviders) {
            try {
                val loc = locationManager.getLastKnownLocation(provider)
                if (loc != null && (loc.latitude != 0.0 || loc.longitude != 0.0)) {
                    if (bestLocation == null || loc.time > bestLocation.time) {
                        bestLocation = loc
                    }
                }
            } catch (_: Exception) {
            }
        }

        // If we have a recent location (< 5 minutes old), use it right away
        if (bestLocation != null && (System.currentTimeMillis() - bestLocation.time) < 5 * 60 * 1000L) {
            saveRealLocation(context, bestLocation.latitude, bestLocation.longitude)
            return GeoPoint(bestLocation.latitude, bestLocation.longitude)
        }

        // Otherwise try to request a fresh location fix within 3.5 seconds
        val freshLocation = withTimeoutOrNull(3500L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val activeProvider = when {
                    runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
                        LocationManager.NETWORK_PROVIDER
                    runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
                        LocationManager.GPS_PROVIDER
                    else -> null
                }

                if (activeProvider == null) {
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }

                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        try {
                            locationManager.removeUpdates(this)
                        } catch (_: Exception) {}
                        if (cont.isActive) {
                            cont.resume(location)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }

                try {
                    locationManager.requestLocationUpdates(
                        activeProvider,
                        0L,
                        0f,
                        listener,
                        Looper.getMainLooper()
                    )
                    cont.invokeOnCancellation {
                        try {
                            locationManager.removeUpdates(listener)
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }

        val finalLoc = freshLocation ?: bestLocation
        if (finalLoc != null && (finalLoc.latitude != 0.0 || finalLoc.longitude != 0.0)) {
            saveRealLocation(context, finalLoc.latitude, finalLoc.longitude)
            return GeoPoint(finalLoc.latitude, finalLoc.longitude)
        }

        return getSavedRealLocation(context) ?: getLastSpoofedLocation(context)
    }

    /**
     * Evaluates device anti-leak posture and calculates the live Stealth Safety Score (up to 99%).
     */
    fun auditSecurityStatus(
        context: Context,
        isSpoofingActive: Boolean,
        isJitterEnabled: Boolean
    ): SecurityAuditStatus {
        val isWifiScanOff = try {
            Settings.Global.getInt(context.contentResolver, "wifi_scan_always_enabled", 0) == 0
        } catch (_: Exception) {
            true
        }

        val isBtScanOff = try {
            Settings.Global.getInt(context.contentResolver, "ble_scan_always_enabled", 0) == 0
        } catch (_: Exception) {
            true
        }

        val isVpnActive = try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(activeNetwork)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        } catch (_: Exception) {
            false
        }

        val isCoordLock = isCoordinateLockEnabled(context)

        // Base engine protections (10Hz Multi-Provider + WakeLock + Anti-Null-Island + Sensor/Satellite Extras) = 70%
        var score = 70
        if (isJitterEnabled) score += 8          // Micro-Jitter + Altitude/Speed Physics Coherence (+8%)
        if (isCoordLock) score += 7              // Coordinate Lock / Anti-Fallback (+7%)
        if (isWifiScanOff) score += 6            // Wi-Fi BSSID Triangulation Blocked (+6%)
        if (isBtScanOff) score += 4              // Bluetooth Beacon Scanning Blocked (+4%)
        if (isVpnActive) score += 4              // IP / WebRTC Geolocation Masked (+4%)

        return SecurityAuditStatus(
            safetyScorePercent = score.coerceAtMost(99),
            isWifiScanningOff = isWifiScanOff,
            isBtScanningOff = isBtScanOff,
            isVpnActive = isVpnActive,
            isCoordinateLockEnabled = isCoordLock,
            isWakeLockActive = isSpoofingActive,
            isJitterActive = isJitterEnabled
        )
    }
}
