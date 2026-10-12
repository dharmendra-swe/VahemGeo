package com.vahemgeo.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.vahemgeo.app.MainActivity
import com.vahemgeo.app.engine.AntiLeakShieldManager
import com.vahemgeo.app.engine.MicroJitterEngine
import com.vahemgeo.app.engine.MockLocationManager
import com.vahemgeo.app.engine.MovementUtils
import com.vahemgeo.app.engine.OsrmRoutingService
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

    // Route Simulation & Auto-Walk parameters
    @Volatile
    private var isRouteMode = false
    private var routePoints: List<GeoPoint> = emptyList()
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
        mockManager = MockLocationManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Anti-Null-Island Recovery: if OS restarts service after memory pressure with null intent
        if (intent == null) {
            val saved = AntiLeakShieldManager.getLastSpoofedLocation(this)
            if (saved != null) {
                currentLat = saved.latitude
                currentLng = saved.longitude
                startForeground(NOTIFICATION_ID, buildNotification(currentLat, currentLng))
                startSpoofingLoop(currentLat, currentLng)
                return START_STICKY
            } else {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val action = intent.action ?: ACTION_START_SPOOFING

        when (action) {
            ACTION_STOP_SPOOFING -> {
                stopSpoofing()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_LOCATION -> {
                isRouteMode = false
                isPaused = false
                _isRoutePaused.value = false
                currentLat = intent.getDoubleExtra(EXTRA_LAT, currentLat)
                currentLng = intent.getDoubleExtra(EXTRA_LNG, currentLng)
                AntiLeakShieldManager.saveSpoofedLocation(this, currentLat, currentLng)
                updateNotification(currentLat, currentLng)
                _currentLocation.value = Pair(currentLat, currentLng)
                return START_STICKY
            }
            ACTION_UPDATE_MOVEMENT -> {
                if (intent.hasExtra(EXTRA_BEARING)) {
                    activeBearing = intent.getFloatExtra(EXTRA_BEARING, activeBearing)
                }
                if (intent.hasExtra(EXTRA_INTENSITY)) {
                    activeIntensity = intent.getFloatExtra(EXTRA_INTENSITY, activeIntensity)
                }
                if (intent.hasExtra(EXTRA_SPEED)) {
                    activeSpeedKmH = intent.getFloatExtra(EXTRA_SPEED, activeSpeedKmH)
                }
                if (intent.hasExtra(EXTRA_AUTO_WALK)) {
                    isAutoWalk = intent.getBooleanExtra(EXTRA_AUTO_WALK, isAutoWalk)
                    _isAutoWalkEnabled.value = isAutoWalk
                }
                _currentSpeed.value = activeSpeedKmH
                return START_STICKY
            }
            ACTION_SET_JITTER -> {
                isStealthJitter = intent.getBooleanExtra(EXTRA_JITTER, true)
                _isJitterEnabled.value = isStealthJitter
                return START_STICKY
            }
            ACTION_SET_AUTO_WALK -> {
                isAutoWalk = intent.getBooleanExtra(EXTRA_AUTO_WALK, false)
                _isAutoWalkEnabled.value = isAutoWalk
                return START_STICKY
            }
            ACTION_SET_ROUTE_PAUSED -> {
                isPaused = intent.getBooleanExtra(EXTRA_ROUTE_PAUSED, false)
                _isRoutePaused.value = isPaused
                updateNotification(currentLat, currentLng)
                return START_STICKY
            }
            ACTION_SET_AUTO_LOOP -> {
                autoLoop = intent.getBooleanExtra(EXTRA_LOOP, false)
                return START_STICKY
            }
            ACTION_START_ROUTE -> {
                val lats = intent.getDoubleArrayExtra(EXTRA_ROUTE_LATS) ?: doubleArrayOf()
                val lngs = intent.getDoubleArrayExtra(EXTRA_ROUTE_LNGS) ?: doubleArrayOf()
                autoLoop = intent.getBooleanExtra(EXTRA_LOOP, false)
                activeSpeedKmH = intent.getFloatExtra(EXTRA_SPEED, activeSpeedKmH)

                if (lats.isNotEmpty() && lats.size == lngs.size) {
                    routePoints = lats.indices.map { GeoPoint(lats[it], lngs[it]) }
                    currentRouteIdx = if (routePoints.size > 1) 1 else 0
                    isRouteMode = true
                    isPaused = false
                    _isRoutePaused.value = false
                    _activeRoute.value = routePoints
                    val totalDist = OsrmRoutingService.computeTotalDistanceMeters(routePoints)
                    _totalRouteDistanceMeters.value = totalDist
                    _remainingDistanceMeters.value = totalDist
                    _routeProgress.value = 0f

                    val startPt = routePoints[0]
                    AntiLeakShieldManager.saveSpoofedLocation(this, startPt.latitude, startPt.longitude)
                    startForeground(NOTIFICATION_ID, buildNotification(startPt.latitude, startPt.longitude))
                    startSpoofingLoop(startPt.latitude, startPt.longitude)
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
                startForeground(NOTIFICATION_ID, buildNotification(currentLat, currentLng))
                startSpoofingLoop(currentLat, currentLng)
            }
        }

        return START_STICKY
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

                    if (isRouteMode && routePoints.isNotEmpty() && !isPaused) {
                        val targetPoint = routePoints[currentRouteIdx]
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
                            if (currentRouteIdx < routePoints.size - 1) {
                                currentRouteIdx++
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

                        // Update live route telemetry (Remaining distance & Progress %)
                        val remDist = calculateRemainingRouteDistance(currentLat, currentLng, currentRouteIdx, routePoints)
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
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            e.printStackTrace()
            _isSpoofingActive.value = false
            releaseWakeLock()
            stopSelf()
        }
    }

    private fun calculateRemainingRouteDistance(
        curLat: Double,
        curLng: Double,
        targetIdx: Int,
        points: List<GeoPoint>
    ): Double {
        if (points.isEmpty() || targetIdx >= points.size) return 0.0
        var dist = GeoPoint(curLat, curLng).distanceToAsDouble(points[targetIdx])
        for (i in targetIdx until points.size - 1) {
            dist += points[i].distanceToAsDouble(points[i + 1])
        }
        return dist
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
        const val EXTRA_ROUTE_LATS = "EXTRA_ROUTE_LATS"
        const val EXTRA_ROUTE_LNGS = "EXTRA_ROUTE_LNGS"
        const val EXTRA_LOOP = "EXTRA_LOOP"
        const val EXTRA_JITTER = "EXTRA_JITTER"
        const val EXTRA_AUTO_WALK = "EXTRA_AUTO_WALK"
        const val EXTRA_ROUTE_PAUSED = "EXTRA_ROUTE_PAUSED"

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
            context.startForegroundService(intent)
        }

        fun startRouteService(context: Context, routePoints: List<GeoPoint>, speedKmH: Float, autoLoop: Boolean) {
            val lats = routePoints.map { it.latitude }.toDoubleArray()
            val lngs = routePoints.map { it.longitude }.toDoubleArray()

            val intent = Intent(context, VahemMockService::class.java).apply {
                action = ACTION_START_ROUTE
                putExtra(EXTRA_ROUTE_LATS, lats)
                putExtra(EXTRA_ROUTE_LNGS, lngs)
                putExtra(EXTRA_SPEED, speedKmH)
                putExtra(EXTRA_LOOP, autoLoop)
            }
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, VahemMockService::class.java).apply {
                action = ACTION_STOP_SPOOFING
            }
            context.startService(intent)
        }

        fun updateLocation(context: Context, lat: Double, lng: Double) {
            val intent = Intent(context, VahemMockService::class.java).apply {
                action = ACTION_UPDATE_LOCATION
                putExtra(EXTRA_LAT, lat)
                putExtra(EXTRA_LNG, lng)
            }
            context.startService(intent)
        }

        fun updateMovement(context: Context, bearing: Float, intensity: Float, speedKmH: Float, autoWalk: Boolean? = null) {
            val intent = Intent(context, VahemMockService::class.java).apply {
                action = ACTION_UPDATE_MOVEMENT
                putExtra(EXTRA_BEARING, bearing)
                putExtra(EXTRA_INTENSITY, intensity)
                putExtra(EXTRA_SPEED, speedKmH)
                if (autoWalk != null) putExtra(EXTRA_AUTO_WALK, autoWalk)
            }
            context.startService(intent)
        }

        fun updateSpeed(context: Context, speedKmH: Float) {
            val intent = Intent(context, VahemMockService::class.java).apply {
                action = ACTION_UPDATE_MOVEMENT
                putExtra(EXTRA_SPEED, speedKmH)
            }
            context.startService(intent)
        }

        fun setJitter(context: Context, enabled: Boolean) {
            val intent = Intent(context, VahemMockService::class.java).apply {
                action = ACTION_SET_JITTER
                putExtra(EXTRA_JITTER, enabled)
            }
            context.startService(intent)
        }

        fun setAutoWalk(context: Context, enabled: Boolean) {
            val intent = Intent(context, VahemMockService::class.java).apply {
                action = ACTION_SET_AUTO_WALK
                putExtra(EXTRA_AUTO_WALK, enabled)
            }
            context.startService(intent)
        }

        fun setRoutePaused(context: Context, paused: Boolean) {
            val intent = Intent(context, VahemMockService::class.java).apply {
                action = ACTION_SET_ROUTE_PAUSED
                putExtra(EXTRA_ROUTE_PAUSED, paused)
            }
            context.startService(intent)
        }

        fun setAutoLoop(context: Context, loop: Boolean) {
            val intent = Intent(context, VahemMockService::class.java).apply {
                action = ACTION_SET_AUTO_LOOP
                putExtra(EXTRA_LOOP, loop)
            }
            context.startService(intent)
        }
    }
}
