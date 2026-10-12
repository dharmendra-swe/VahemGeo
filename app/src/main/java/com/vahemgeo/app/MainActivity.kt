package com.vahemgeo.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.vahemgeo.app.data.local.AppDatabase
import com.vahemgeo.app.data.local.BookmarkEntity
import com.vahemgeo.app.data.local.HistoryEntity
import com.vahemgeo.app.service.JoystickOverlayService
import com.vahemgeo.app.service.VahemMockService
import com.vahemgeo.app.ui.MapScreen
import com.vahemgeo.app.ui.theme.VahemGeoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase
    private var hasLocationPermission by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            hasLocationPermission = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        database = AppDatabase.getDatabase(this)
        checkAndRequestPermissions()

        setContent {
            VahemGeoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val scope = rememberCoroutineScope()

                    val isSpoofingActive by VahemMockService.isSpoofingActive.collectAsState()
                    val currentLocation by VahemMockService.currentLocation.collectAsState()
                    val currentSpeed by VahemMockService.currentSpeed.collectAsState()
                    val routeProgress by VahemMockService.routeProgress.collectAsState()
                    val remainingDistanceMeters by VahemMockService.remainingDistanceMeters.collectAsState()
                    val totalRouteDistanceMeters by VahemMockService.totalRouteDistanceMeters.collectAsState()
                    val isRoutePaused by VahemMockService.isRoutePaused.collectAsState()
                    val isOverlayRunning by JoystickOverlayService.isOverlayRunning.collectAsState()
                    val isJitterEnabled by VahemMockService.isJitterEnabled.collectAsState()
                    val isAutoWalkEnabled by VahemMockService.isAutoWalkEnabled.collectAsState()
                    val securityError by VahemMockService.securityError.collectAsState()

                    val bookmarks by database.bookmarkDao().getAllBookmarks().collectAsState(initial = emptyList())
                    val historyLogs by database.historyDao().getAllHistory().collectAsState(initial = emptyList())

                    MapScreen(
                        hasLocationPermission = hasLocationPermission,
                        isSpoofingActive = isSpoofingActive,
                        currentLocation = currentLocation,
                        currentSpeed = currentSpeed,
                        routeProgress = routeProgress,
                        remainingDistanceMeters = remainingDistanceMeters,
                        totalRouteDistanceMeters = totalRouteDistanceMeters,
                        isRoutePaused = isRoutePaused,
                        isOverlayRunning = isOverlayRunning,
                        isJitterEnabled = isJitterEnabled,
                        isAutoWalkEnabled = isAutoWalkEnabled,
                        securityError = securityError,
                        bookmarks = bookmarks,
                        historyLogs = historyLogs,
                        onStartSpoofing = { lat, lng, speedKmH, label ->
                            VahemMockService.startService(this, lat, lng, speedKmH)
                            scope.launch {
                                database.historyDao().insertHistory(
                                    HistoryEntity(label = label, latitude = lat, longitude = lng)
                                )
                            }
                        },
                        onStartRouteSpoofing = { points, speedKmH, autoLoop, routeLabel ->
                            VahemMockService.startRouteService(this, points, speedKmH, autoLoop)
                            if (points.isNotEmpty()) {
                                scope.launch {
                                    database.historyDao().insertHistory(
                                        HistoryEntity(
                                            label = routeLabel.ifBlank { "Route (${points.size} pts)" },
                                            latitude = points[0].latitude,
                                            longitude = points[0].longitude
                                        )
                                    )
                                }
                            }
                        },
                        onStopSpoofing = {
                            VahemMockService.stopService(this)
                        },
                        onUpdateLocation = { lat, lng ->
                            if (isSpoofingActive) {
                                VahemMockService.updateLocation(this, lat, lng)
                            }
                        },
                        onUpdateMovement = { bearing, intensity, speedKmH ->
                            if (isSpoofingActive) {
                                VahemMockService.updateMovement(this, bearing, intensity, speedKmH)
                            }
                        },
                        onSetJitter = { enabled ->
                            VahemMockService.setJitter(this, enabled)
                        },
                        onSetAutoWalk = { enabled ->
                            VahemMockService.setAutoWalk(this, enabled)
                        },
                        onSetRoutePaused = { paused ->
                            VahemMockService.setRoutePaused(this, paused)
                        },
                        onSetAutoLoop = { loop ->
                            VahemMockService.setAutoLoop(this, loop)
                        },
                        onAddBookmark = { label, lat, lng, notes ->
                            scope.launch {
                                database.bookmarkDao().insertBookmark(
                                    BookmarkEntity(label = label, latitude = lat, longitude = lng, notes = notes)
                                )
                            }
                        },
                        onDeleteBookmark = { bookmark ->
                            scope.launch {
                                database.bookmarkDao().deleteBookmark(bookmark)
                            }
                        },
                        onClearHistory = {
                            scope.launch {
                                database.historyDao().clearHistory()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        hasLocationPermission = fineGranted || coarseGranted

        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
}