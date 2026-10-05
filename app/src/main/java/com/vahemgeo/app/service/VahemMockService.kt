package com.vahemgeo.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vahemgeo.app.MainActivity
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

class VahemMockService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var mockManager: MockLocationManager
    private var spoofJob: Job? = null

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

    override fun onCreate() {
        super.onCreate()
        mockManager = MockLocationManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SPOOFING

        when (action) {
            ACTION_STOP_SPOOFING -> {
                stopSpoofing()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_LOCATION -> {
                currentLat = intent?.getDoubleExtra(EXTRA_LAT, currentLat) ?: currentLat
                currentLng = intent?.getDoubleExtra(EXTRA_LNG, currentLng) ?: currentLng
                updateNotification(currentLat, currentLng)
                _currentLocation.value = Pair(currentLat, currentLng)
                return START_STICKY
            }
            ACTION_UPDATE_MOVEMENT -> {
                activeBearing = intent?.getFloatExtra(EXTRA_BEARING, activeBearing) ?: activeBearing
                activeIntensity = intent?.getFloatExtra(EXTRA_INTENSITY, activeIntensity) ?: activeIntensity
                activeSpeedKmH = intent?.getFloatExtra(EXTRA_SPEED, activeSpeedKmH) ?: activeSpeedKmH
                _currentSpeed.value = activeSpeedKmH
                return START_STICKY
            }
            ACTION_START_SPOOFING -> {
                currentLat = intent?.getDoubleExtra(EXTRA_LAT, 0.0) ?: 0.0
                currentLng = intent?.getDoubleExtra(EXTRA_LNG, 0.0) ?: 0.0
                activeSpeedKmH = intent?.getFloatExtra(EXTRA_SPEED, activeSpeedKmH) ?: activeSpeedKmH

                startForeground(NOTIFICATION_ID, buildNotification(currentLat, currentLng))
                startSpoofingLoop(currentLat, currentLng)
            }
        }

        return START_STICKY
    }

    private fun startSpoofingLoop(lat: Double, lng: Double) {
        spoofJob?.cancel()
        currentLat = lat
        currentLng = lng
        _currentLocation.value = Pair(currentLat, currentLng)
        _currentSpeed.value = activeSpeedKmH

        try {
            mockManager.setupMockProviders()
            _isSpoofingActive.value = true
            _securityError.value = null

            val updateIntervalMs = 500L // 2 updates per second for smooth joystick movement

            spoofJob = serviceScope.launch {
                while (_isSpoofingActive.value) {
                    if (activeIntensity > 0.05f) {
                        val effectiveSpeed = activeSpeedKmH * activeIntensity
                        val distanceMeters = MovementUtils.speedToMetersPerInterval(effectiveSpeed, updateIntervalMs)
                        val (nextLat, nextLng) = MovementUtils.calculateNextCoordinates(
                            currentLat, currentLng, distanceMeters, activeBearing
                        )
                        currentLat = nextLat
                        currentLng = nextLng
                        _currentLocation.value = Pair(currentLat, currentLng)
                    }

                    mockManager.pushLocation(currentLat, currentLng, bearing = activeBearing)
                    delay(updateIntervalMs)
                }
            }
        } catch (e: SecurityException) {
            _securityError.value = "Mock Location permission disabled in Developer Options"
            _isSpoofingActive.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            e.printStackTrace()
            _isSpoofingActive.value = false
            stopSelf()
        }
    }

    private fun stopSpoofing() {
        _isSpoofingActive.value = false
        activeIntensity = 0f
        spoofJob?.cancel()
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VahemGeo Location Spoofer")
            .setContentText("Active: ${"%.5f".format(lat)}, ${"%.5f".format(lng)} | ${activeSpeedKmH.toInt()} km/h")
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

        const val EXTRA_LAT = "EXTRA_LAT"
        const val EXTRA_LNG = "EXTRA_LNG"
        const val EXTRA_BEARING = "EXTRA_BEARING"
        const val EXTRA_INTENSITY = "EXTRA_INTENSITY"
        const val EXTRA_SPEED = "EXTRA_SPEED"

        private val _isSpoofingActive = MutableStateFlow(false)
        val isSpoofingActive: StateFlow<Boolean> = _isSpoofingActive.asStateFlow()

        private val _currentLocation = MutableStateFlow<Pair<Double, Double>?>(null)
        val currentLocation: StateFlow<Pair<Double, Double>?> = _currentLocation.asStateFlow()

        private val _currentSpeed = MutableStateFlow(SpeedMode.WALK.speedKmH)
        val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()

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

        fun updateMovement(context: Context, bearing: Float, intensity: Float, speedKmH: Float) {
            val intent = Intent(context, VahemMockService::class.java).apply {
                action = ACTION_UPDATE_MOVEMENT
                putExtra(EXTRA_BEARING, bearing)
                putExtra(EXTRA_INTENSITY, intensity)
                putExtra(EXTRA_SPEED, speedKmH)
            }
            context.startService(intent)
        }
    }
}
