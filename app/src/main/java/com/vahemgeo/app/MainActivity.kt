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
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.vahemgeo.app.service.JoystickOverlayService
import com.vahemgeo.app.service.VahemMockService
import com.vahemgeo.app.ui.MapScreen
import com.vahemgeo.app.ui.theme.VahemGeoTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestPermissions()

        setContent {
            VahemGeoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isSpoofingActive by VahemMockService.isSpoofingActive.collectAsState()
                    val currentLocation by VahemMockService.currentLocation.collectAsState()
                    val currentSpeed by VahemMockService.currentSpeed.collectAsState()
                    val isOverlayRunning by JoystickOverlayService.isOverlayRunning.collectAsState()
                    val securityError by VahemMockService.securityError.collectAsState()

                    MapScreen(
                        isSpoofingActive = isSpoofingActive,
                        currentLocation = currentLocation,
                        currentSpeed = currentSpeed,
                        isOverlayRunning = isOverlayRunning,
                        securityError = securityError,
                        onStartSpoofing = { lat, lng, speedKmH ->
                            VahemMockService.startService(this, lat, lng, speedKmH)
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