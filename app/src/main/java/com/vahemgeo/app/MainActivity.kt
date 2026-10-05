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
import androidx.compose.runtime.rememberCoroutineScope
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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled
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
                    val isOverlayRunning by JoystickOverlayService.isOverlayRunning.collectAsState()
                    val isJitterEnabled by VahemMockService.isJitterEnabled.collectAsState()
                    val securityError by VahemMockService.securityError.collectAsState()

                    val bookmarks by database.bookmarkDao().getAllBookmarks().collectAsState(initial = emptyList())
                    val historyLogs by database.historyDao().getAllHistory().collectAsState(initial = emptyList())

                    MapScreen(
                        isSpoofingActive = isSpoofingActive,
                        currentLocation = currentLocation,
                        currentSpeed = currentSpeed,
                        isOverlayRunning = isOverlayRunning,
                        isJitterEnabled = isJitterEnabled,
                        securityError = securityError,
                        bookmarks = bookmarks,
                        historyLogs = historyLogs,
                        onStartSpoofing = { lat, lng, speedKmH ->
                            VahemMockService.startService(this, lat, lng, speedKmH)
                            scope.launch {
                                database.historyDao().insertHistory(
                                    HistoryEntity(latitude = lat, longitude = lng)
                                )
                            }
                        },
                        onStartRouteSpoofing = { points, speedKmH, autoLoop ->
                            VahemMockService.startRouteService(this, points, speedKmH, autoLoop)
                            if (points.isNotEmpty()) {
                                scope.launch {
                                    database.historyDao().insertHistory(
                                        HistoryEntity(label = "Route (${points.size} pts)", latitude = points[0].latitude, longitude = points[0].longitude)
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