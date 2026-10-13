package com.vahemgeo.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.vahemgeo.app.MainActivity
import com.vahemgeo.app.engine.AntiLeakShieldManager
import com.vahemgeo.app.engine.MicroJitterEngine
import com.vahemgeo.app.engine.MockLocationManager
import com.vahemgeo.app.engine.MovementUtils
import com.vahemgeo.app.engine.SpeedMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

class VahemMockService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var mockManager: MockLocationManager
    private var spoofJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var currentLat: Double = 0.0
    @Volatile
    private var currentLng: Double = 0.0

    @Volatile
    private var activeSpeedKmH: Float = SpeedMode.WALK.speedKmH
    @Volatile
    private var activeBearing: Float = 0f
    @Volatile
    private var activeIntensity: Float = 0f

    // Route Simulation & O(1) suffix distance lookup table
    @Volatile
    private var isRouteMode = false
    @Volatile
    private var routePoints: List<GeoPoint> = emptyList()
    @Volatile
    private var routeSuffixDistances: DoubleArray = DoubleArray(0)
    @Volatile
    private var currentRouteIdx = 0
    @Volatile
    private var autoLoop = false
    @Volatile
    private var isStealthJitter = true
    @Volatile
    private var isAutoWalk = false
    @Volatile
    private var isPaused = false

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        mockManager = MockLocationManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeInstance = this

        // Anti-Null-Island Recovery: if OS restarts service after memory pressure with null intent
        if (intent == null) {
            val saved = AntiLeakShieldManager.getLastSpoofedLocation(this)
            if (saved != null) {
                currentLat = saved.latitude
                currentLng = saved.longitude
                if (startForegroundSafely(currentLat, currentLng)) {
                    startSpoofingLoop(currentLat, currentLng)
                }
                return START_STICKY
            } else {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val action = intent.action ?: ACTION_START_SPOOFING

        when (action) {
            ACTION_STOP_SPOOFING -> {
                stopSpoofingInternal()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_LOCATION -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, currentLat)
                val lng = intent.getDoubleExtra(EXTRA_LNG, currentLng)
                applyLocationUpdateInMemory(lat, lng)
                return START_STICKY
            }
            ACTION_UPDATE_MOVEMENT -> {
                val bearing = if (intent.hasExtra(EXTRA_BEARING)) intent.getFloatExtra(EXTRA_BEARING, activeBearing) else null
                val intensity = if (intent.hasExtra(EXTRA_INTENSITY)) intent.getFloatExtra(EXTRA_INTENSITY, activeIntensity) else null
                val speed = if (intent.hasExtra(EXTRA_SPEED)) intent.getFloatExtra(EXTRA_SPEED, activeSpeedKmH) else null
                val walk = if (intent.hasExtra(EXTRA_AUTO_WALK)) intent.getBooleanExtra(EXTRA_AUTO_WALK, isAutoWalk) else null
                applyMovementUpdateInMemory(bearing, intensity, speed, walk)
                return START_STICKY
            }
            ACTION_SET_JITTER -> {
                applyJitterInMemory(intent.getBooleanExtra(EXTRA_JITTER, true))
                return START_STICKY
            }
            ACTION_SET_AUTO_WALK -> {
                applyAutoWalkInMemory(intent.getBooleanExtra(EXTRA_AUTO_WALK, false))
                return START_STICKY
            }
            ACTION_SET_ROUTE_PAUSED -> {
                applyRoutePausedInMemory(intent.getBooleanExtra(EXTRA_ROUTE_PAUSED, false))
                return START_STICKY
            }
            ACTION_SET_AUTO_LOOP -> {
                autoLoop = intent.getBooleanExtra(EXTRA_LOOP, false)
                return START_STICKY
            }
            ACTION_START_ROUTE -> {
                autoLoop = intent.getBooleanExtra(EXTRA_LOOP, false)
                activeSpeedKmH = intent.getFloatExtra(EXTRA_SPEED, activeSpeedKmH)

                val points = pendingRoutePoints
                if (points.isNotEmpty()) {
                    routePoints = points
                    routeSuffixDistances = buildSuffixDistanceTable(points)
                    currentRouteIdx = if (points.size > 1) 1 else 0
                    isRouteMode = true
                    isPaused = false
                    _isRoutePaused.value = false
                    _activeRoute.value = points
                    val totalDist = if (routeSuffixDistances.isNotEmpty()) routeSuffixDistances[0] else 0.0
                    _totalRouteDistanceMeters.value = totalDist
                    _remainingDistanceMeters.value = totalDist
                    _routeProgress.value = 0f

                    val startPt = points[0]
                    AntiLeakShieldManager.saveSpoofedLocation(this, startPt.latitude, startPt.longitude)
                    if (startForegroundSafely(startPt.latitude, startPt.longitude)) {
                        startSpoofingLoop(startPt.latitude, startPt.longitude)
                    }
                }
                return START_STICKY
            }
            ACTION_START_SPOOFING -> {
                isRouteMode = false
                isPaused = false
                _isRoutePaused.value = false
                _routeProgress.value = 0f
                _remainingDistanceMeters.value = 0.0
                val fallback = AntiLeakShieldManager.getLastSpoofedLocation(this)
                    ?: AntiLeakShieldManager.DEFAULT_FALLBACK_POINT
                currentLat = intent.getDoubleExtra(EXTRA_LAT, fallback.latitude)
                currentLng = intent.getDoubleExtra(EXTRA_LNG, fallback.longitude)
                activeSpeedKmH = intent.getFloatExtra(EXTRA_SPEED, activeSpeedKmH)

                AntiLeakShieldManager.saveSpoofedLocation(this, currentLat, currentLng)
                if (startForegroundSafely(currentLat, currentLng)) {
                    startSpoofingLoop(currentLat, currentLng)
                }
            }
        }

        return START_STICKY
    }

    /**
     * Safely promotes service to Foreground with ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION on Android 14/15 (API 34/35).
     */
    private fun startForegroundSafely(lat: Double, lng: Double): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            _securityError.value = "Grant Location Permission first to start Foreground Spoof Service"
            _isSpoofingActive.value = false
            stopSelf()
            return false
        }

        return try {
            val notification = buildNotification(lat, lng)
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            _securityError.value = "Unable to start Foreground Location Service: ${e.localizedMessage ?: "Permission error"}"
            _isSpoofingActive.value = false
            stopSelf()
            false
        }
    }

    private fun applyLocationUpdateInMemory(lat: Double, lng: Double) {
        isRouteMode = false
        isPaused = false
        _isRoutePaused.value = false
        currentLat = lat
        currentLng = lng
        AntiLeakShieldManager.saveSpoofedLocation(this, currentLat, currentLng)
        updateNotification(currentLat, currentLng)
        _currentLocation.value = Pair(currentLat, currentLng)
    }

    private fun applyMovementUpdateInMemory(
        bearing: Float?,
        intensity: Float?,
        speedKmH: Float?,
        autoWalk: Boolean?
    ) {
        if (bearing != null) activeBearing = bearing
        if (intensity != null) activeIntensity = intensity
        if (speedKmH != null) {
            activeSpeedKmH = speedKmH
            _currentSpeed.value = activeSpeedKmH
        }
        if (autoWalk != null) {
            isAutoWalk = autoWalk
            _isAutoWalkEnabled.value = isAutoWalk
        }
    }

    private fun applyJitterInMemory(enabled: Boolean) {
        isStealthJitter = enabled
        _isJitterEnabled.value = enabled
    }

    private fun applyAutoWalkInMemory(enabled: Boolean) {
        isAutoWalk = enabled
        _isAutoWalkEnabled.value = enabled
    }

    private fun applyRoutePausedInMemory(paused: Boolean) {
        isPaused = paused
        _isRoutePaused.value = paused
        updateNotification(currentLat, currentLng)
    }

    private fun stopSpoofingInternal() {
        stopSpoofing()
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        stopSelf()
    }

    /**
     * Precomputes suffix distances in O(N) once so 10Hz route progress updates run in O(1) time.
     * table[i] = remaining distance along path from points[i] to points.last()
     */
    private fun buildSuffixDistanceTable(points: List<GeoPoint>): DoubleArray {
        val n = points.size
        if (n == 0) return DoubleArray(0)
        val table = DoubleArray(n)
        table[n - 1] = 0.0
        for (i in n - 2 downTo 0) {
            table[i] = table[i + 1] + points[i].distanceToAsDouble(points[i + 1])
        }
        return table
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "VahemGeo::AntiLeakMockWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // Up to 12 hours continuous protection
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startSpoofingLoop(lat: Double, lng: Double) {
        spoofJob?.cancel()
        currentLat = lat
        currentLng = lng
        _currentLocation.value = Pair(currentLat, currentLng)
        _currentSpeed.value = activeSpeedKmH

        try {
            mockManager.setupMockProviders()
            acquireWakeLock()
            _isSpoofingActive.value = true
            _securityError.value = null

            // High-precision 10 Hz (100ms) anti-bleed injection cadence
            val updateIntervalMs = 100L
            var tickCount = 0

            spoofJob = serviceScope.launch {
                while (_isSpoofingActive.value) {
                    var currentSpeedMps = 0f
                    val pointsSnapshot = routePoints
                    val suffixSnapshot = routeSuffixDistances

                    if (isRouteMode && pointsSnapshot.isNotEmpty() && !isPaused) {
                        val safeIdx = currentRouteIdx.coerceIn(0, pointsSnapshot.size - 1)
                        val targetPoint = pointsSnapshot[safeIdx]
                        val dynamicSpeed = MovementUtils.applySpeedVariance(activeSpeedKmH)
                        val stepDistance = MovementUtils.speedToMetersPerInterval(dynamicSpeed, updateIntervalMs)
                        currentSpeedMps = (dynamicSpeed / 3.6f)

                        val currentGeo = GeoPoint(currentLat, currentLng)
                        val distToTarget = currentGeo.distanceToAsDouble(targetPoint)
                        val bearingToTarget = MovementUtils.calculateBearing(
                            currentLat, currentLng, targetPoint.latitude, targetPoint.longitude
                        )
                        activeBearing = MovementUtils.interpolateBearing(activeBearing, bearingToTarget, 22f)

                        if (distToTarget <= stepDistance) {
                            currentLat = targetPoint.latitude
                            currentLng = targetPoint.longitude
                            if (safeIdx < pointsSnapshot.size - 1) {
                                currentRouteIdx = safeIdx + 1
                            } else if (autoLoop) {
                                currentRouteIdx = 0
                            } else {
                                // Reached final destination B: stay spoofed at destination!
                                currentSpeedMps = 0f
                                isPaused = true
                                _isRoutePaused.value = true
                            }
                        } else {
                            val (nextLat, nextLng) = MovementUtils.calculateNextCoordinates(
                                currentLat, currentLng, stepDistance, activeBearing
                            )
                            currentLat = nextLat
                            currentLng = nextLng
                        }

                        // O(1) Remaining Route Distance lookup (zero CPU lag even on 50,000-point GPX routes)
                        val activeIdx = currentRouteIdx.coerceIn(0, pointsSnapshot.size - 1)
                        val distToActiveTarget = GeoPoint(currentLat, currentLng).distanceToAsDouble(pointsSnapshot[activeIdx])
                        val suffixDist = if (activeIdx < suffixSnapshot.size) suffixSnapshot[activeIdx] else 0.0
                        val remDist = distToActiveTarget + suffixDist

                        _remainingDistanceMeters.value = remDist
                        val totalDist = _totalRouteDistanceMeters.value
                        _routeProgress.value = if (totalDist > 1.0) {
                            ((totalDist - remDist) / totalDist).toFloat().coerceIn(0f, 1f)
                        } else {
                            1f
                        }

                        _currentLocation.value = Pair(currentLat, currentLng)
                    } else if (!isRouteMode && (isAutoWalk || activeIntensity > 0.05f)) {
                        val intensityMultiplier = if (activeIntensity > 0.05f) activeIntensity else 1.0f
                        val effectiveSpeed = activeSpeedKmH * intensityMultiplier
                        val dynamicSpeed = MovementUtils.applySpeedVariance(effectiveSpeed)
                        currentSpeedMps = (dynamicSpeed / 3.6f)

                        val distanceMeters = MovementUtils.speedToMetersPerInterval(dynamicSpeed, updateIntervalMs)
                        val (nextLat, nextLng) = MovementUtils.calculateNextCoordinates(
                            currentLat, currentLng, distanceMeters, activeBearing
                        )
                        currentLat = nextLat
                        currentLng = nextLng
                        _currentLocation.value = Pair(currentLat, currentLng)
                    } else {
                        currentSpeedMps = 0f
                    }

                    // Apply Micro-Jitter (sub-meter natural GPS drift)
                    val (finalLat, finalLng) = MicroJitterEngine.applyJitter(currentLat, currentLng, isStealthJitter)
                    mockManager.pushLocation(
                        lat = finalLat,
                        lng = finalLng,
                        altitude = 215.0,
                        bearing = activeBearing,
                        speed = currentSpeedMps,
                        isJitterActive = isStealthJitter
                    )

                    // Persist coordinates every 2 seconds (20 ticks) for anti-leak recovery
                    tickCount++
                    if (tickCount % 20 == 0) {
                        AntiLeakShieldManager.saveSpoofedLocation(this@VahemMockService, currentLat, currentLng)
                        if (tickCount % 50 == 0) {
                            updateNotification(currentLat, currentLng)
                        }
                    }

                    delay(updateIntervalMs)
                }
            }
        } catch (e: SecurityException) {
            _securityError.value = "Mock Location permission disabled in Developer Options"
            _isSpoofingActive.value = false
            releaseWakeLock()
            try {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {}
            stopSelf()
        } catch (e: Exception) {
            e.printStackTrace()
            _isSpoofingActive.value = false
            releaseWakeLock()
            stopSelf()
        }
    }

    private fun stopSpoofing() {
        _isSpoofingActive.value = false
        isRouteMode = false
        isAutoWalk = false
        isPaused = false
        _isAutoWalkEnabled.value = false
        _isRoutePaused.value = false
        _routeProgress.value = 0f
        _remainingDistanceMeters.value = 0.0
        activeIntensity = 0f
        _activeRoute.value = emptyList()
        spoofJob?.cancel()
        releaseWakeLock()
        mockManager.removeMockProviders()
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        stopSpoofing()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(lat: Double, lng: Double) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(lat, lng))
    }

    private fun buildNotification(lat: Double, lng: Double): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VahemMockService::class.java).apply {
            action = ACTION_STOP_SPOOFING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val modeLabel = when {
            isRouteMode && isPaused -> "Route Paused/Arrived"
            isRouteMode -> "Route Moving (${(_routeProgress.value * 100).toInt()}%)"
            isAutoWalk -> "Auto-Walk Active"
            else -> "Stealth Spoofing"
        }
        val statusText = "$modeLabel (${activeSpeedKmH.toInt()} km/h) | ${"%.5f".format(lat)}, ${"%.5f".format(lng)}"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VahemGeo Anti-Leak Shield Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Spoofing", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VahemGeo Spoof Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification shown while GPS location spoofing is active"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "VAHEM_GEO_CHANNEL"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_SPOOFING = "com.vahemgeo.app.START_SPOOFING"
        const val ACTION_STOP_SPOOFING = "com.vahemgeo.app.STOP_SPOOFING"
        const val ACTION_UPDATE_LOCATION = "com.vahemgeo.app.UPDATE_LOCATION"
        const val ACTION_UPDATE_MOVEMENT = "com.vahemgeo.app.UPDATE_MOVEMENT"
        const val ACTION_START_ROUTE = "com.vahemgeo.app.START_ROUTE"
        const val ACTION_SET_JITTER = "com.vahemgeo.app.SET_JITTER"
        const val ACTION_SET_AUTO_WALK = "com.vahemgeo.app.SET_AUTO_WALK"
        const val ACTION_SET_ROUTE_PAUSED = "com.vahemgeo.app.SET_ROUTE_PAUSED"
        const val ACTION_SET_AUTO_LOOP = "com.vahemgeo.app.SET_AUTO_LOOP"

        const val EXTRA_LAT = "EXTRA_LAT"
        const val EXTRA_LNG = "EXTRA_LNG"
        const val EXTRA_BEARING = "EXTRA_BEARING"
        const val EXTRA_INTENSITY = "EXTRA_INTENSITY"
        const val EXTRA_SPEED = "EXTRA_SPEED"
        const val EXTRA_LOOP = "EXTRA_LOOP"
        const val EXTRA_JITTER = "EXTRA_JITTER"
        const val EXTRA_AUTO_WALK = "EXTRA_AUTO_WALK"
        const val EXTRA_ROUTE_PAUSED = "EXTRA_ROUTE_PAUSED"

        @Volatile
        private var activeInstance: VahemMockService? = null

        // In-memory route buffer so arbitrarily large GPX / OSRM routes never trigger TransactionTooLargeException
        @Volatile
        private var pendingRoutePoints: List<GeoPoint> = emptyList()

        private val _isSpoofingActive = MutableStateFlow(false)
        val isSpoofingActive: StateFlow<Boolean> = _isSpoofingActive.asStateFlow()

        private val _currentLocation = MutableStateFlow<Pair<Double, Double>?>(null)
        val currentLocation: StateFlow<Pair<Double, Double>?> = _currentLocation.asStateFlow()

        private val _currentSpeed = MutableStateFlow(SpeedMode.WALK.speedKmH)
        val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()

        private val _activeRoute = MutableStateFlow<List<GeoPoint>>(emptyList())
        val activeRoute: StateFlow<List<GeoPoint>> = _activeRoute.asStateFlow()

        private val _routeProgress = MutableStateFlow(0f)
        val routeProgress: StateFlow<Float> = _routeProgress.asStateFlow()

        private val _remainingDistanceMeters = MutableStateFlow(0.0)
        val remainingDistanceMeters: StateFlow<Double> = _remainingDistanceMeters.asStateFlow()

        private val _totalRouteDistanceMeters = MutableStateFlow(0.0)
        val totalRouteDistanceMeters: StateFlow<Double> = _totalRouteDistanceMeters.asStateFlow()

        private val _isRoutePaused = MutableStateFlow(false)
        val isRoutePaused: StateFlow<Boolean> = _isRoutePaused.asStateFlow()

        private val _isJitterEnabled = MutableStateFlow(true)
        val isJitterEnabled: StateFlow<Boolean> = _isJitterEnabled.asStateFlow()

        private val _isAutoWalkEnabled = MutableStateFlow(false)
        val isAutoWalkEnabled: StateFlow<Boolean> = _isAutoWalkEnabled.asStateFlow()

        private val _securityError = MutableStateFlow<String?>(null)
        val securityError: StateFlow<String?> = _securityError.asStateFlow()

        fun startService(context: Context, lat: Double, lng: Double, speedKmH: Float = SpeedMode.WALK.speedKmH) {
            val intent = Intent(context, VahemMockService::class.java).apply {
                action = ACTION_START_SPOOFING
                putExtra(EXTRA_LAT, lat)
                putExtra(EXTRA_LNG, lng)
                putExtra(EXTRA_SPEED, speedKmH)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startRouteService(context: Context, routePoints: List<GeoPoint>, speedKmH: Float, autoLoop: Boolean) {
            pendingRoutePoints = routePoints.toList()
            val intent = Intent(context, VahemMockService::class.java).apply {
                action = ACTION_START_ROUTE
                putExtra(EXTRA_SPEED, speedKmH)
                putExtra(EXTRA_LOOP, autoLoop)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val instance = activeInstance
            if (instance != null) {
                instance.stopSpoofingInternal()
            } else {
                try {
                    context.stopService(Intent(context, VahemMockService::class.java))
                } catch (_: Exception) {}
            }
        }

        fun updateLocation(context: Context, lat: Double, lng: Double) {
            val instance = activeInstance
            if (instance != null) {
                instance.applyLocationUpdateInMemory(lat, lng)
            } else if (_isSpoofingActive.value) {
                startService(context, lat, lng, _currentSpeed.value)
            }
        }

        fun updateMovement(context: Context, bearing: Float, intensity: Float, speedKmH: Float, autoWalk: Boolean? = null) {
            val instance = activeInstance
            if (instance != null) {
                instance.applyMovementUpdateInMemory(bearing, intensity, speedKmH, autoWalk)
            } else {
                _currentSpeed.value = speedKmH
                if (autoWalk != null) _isAutoWalkEnabled.value = autoWalk
            }
        }

        fun updateSpeed(context: Context, speedKmH: Float) {
            val instance = activeInstance
            if (instance != null) {
                instance.applyMovementUpdateInMemory(null, null, speedKmH, null)
            } else {
                _currentSpeed.value = speedKmH
            }
        }

        fun setJitter(context: Context, enabled: Boolean) {
            val instance = activeInstance
            if (instance != null) {
                instance.applyJitterInMemory(enabled)
            } else {
                _isJitterEnabled.value = enabled
            }
        }

        fun setAutoWalk(context: Context, enabled: Boolean) {
            val instance = activeInstance
            if (instance != null) {
                instance.applyAutoWalkInMemory(enabled)
            } else {
                _isAutoWalkEnabled.value = enabled
            }
        }

        fun setRoutePaused(context: Context, paused: Boolean) {
            val instance = activeInstance
            if (instance != null) {
                instance.applyRoutePausedInMemory(paused)
            } else {
                _isRoutePaused.value = paused
            }
        }

        fun setAutoLoop(context: Context, loop: Boolean) {
            val instance = activeInstance
            if (instance != null) {
                instance.autoLoop = loop
            }
        }
    }
}
