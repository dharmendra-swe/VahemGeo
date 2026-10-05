package com.vahemgeo.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vahemgeo.app.engine.SpeedMode
import com.vahemgeo.app.service.JoystickOverlayService
import com.vahemgeo.app.ui.components.JoystickView
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

@Composable
fun MapScreen(
    isSpoofingActive: Boolean,
    currentLocation: Pair<Double, Double>?,
    currentSpeed: Float,
    isOverlayRunning: Boolean,
    securityError: String?,
    onStartSpoofing: (Double, Double, Float) -> Unit,
    onStopSpoofing: () -> Unit,
    onUpdateLocation: (Double, Double) -> Unit,
    onUpdateMovement: (Float, Float, Float) -> Unit
) {
    val context = LocalContext.current
    var selectedLocation by remember { mutableStateOf(GeoPoint(23.2599, 77.4126)) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var markerRef by remember { mutableStateOf<Marker?>(null) }

    var selectedSpeedKmH by remember { mutableFloatStateOf(SpeedMode.WALK.speedKmH) }

    // Synchronize location updates from mock service
    LaunchedEffect(currentLocation) {
        if (isSpoofingActive && currentLocation != null) {
            val point = GeoPoint(currentLocation.first, currentLocation.second)
            selectedLocation = point
            markerRef?.position = point
            mapViewRef?.controller?.animateTo(point)
            mapViewRef?.invalidate()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapViewRef?.onDetach()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Map View
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setMultiTouchControls(true)
                    controller.setZoom(16.0)
                    controller.setCenter(selectedLocation)

                    val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            if (!isSpoofingActive) {
                                selectedLocation = p
                                markerRef?.position = p
                                invalidate()
                            }
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean = false
                    })
                    overlays.add(eventsOverlay)

                    val marker = Marker(this).apply {
                        position = selectedLocation
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Target Location"
                        snippet = "${selectedLocation.latitude}, ${selectedLocation.longitude}"
                    }
                    markerRef = marker
                    overlays.add(marker)

                    mapViewRef = this
                }
            },
            update = { mapView ->
                markerRef?.position = selectedLocation
                markerRef?.snippet = "${selectedLocation.latitude}, ${selectedLocation.longitude}"
                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Status Header & Safe Inset Warning (Safe Area Padding)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
        ) {
            // Status Header Pill
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 6.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (isSpoofingActive) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSpoofingActive) "SPOOFING ACTIVE" else "IDLE - READY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Warning Card for Mock Location in Developer Options
            AnimatedVisibility(visible = securityError != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mock Location Not Set",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Select 'VahemGeo' in Developer Options",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_SETTINGS)
                                    context.startActivity(intent)
                                }
                            }
                        ) {
                            Text("Open", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // On-Screen Joystick positioned safely above bottom control card
        AnimatedVisibility(
            visible = isSpoofingActive,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 320.dp)
        ) {
            JoystickView(
                size = 130.dp,
                knobSize = 48.dp,
                onJoystickMoved = { bearing, intensity ->
                    onUpdateMovement(bearing, intensity, selectedSpeedKmH)
                },
                onJoystickReleased = {
                    onUpdateMovement(0f, 0f, selectedSpeedKmH)
                }
            )
        }

        // Bottom Responsive Control Panel Card with Navigation Insets
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Coordinates Display
                Text(
                    text = "${"%.6f".format(selectedLocation.latitude)}, ${"%.6f".format(selectedLocation.longitude)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Material 3 Responsive Segmented Speed Selector (No vertical squishing!)
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SpeedMode.entries.forEachIndexed { index, mode ->
                        val icon = when (mode) {
                            SpeedMode.WALK -> Icons.Default.DirectionsWalk
                            SpeedMode.CYCLE -> Icons.Default.DirectionsBike
                            SpeedMode.DRIVE -> Icons.Default.DirectionsCar
                        }
                        SegmentedButton(
                            selected = selectedSpeedKmH == mode.speedKmH,
                            onClick = {
                                selectedSpeedKmH = mode.speedKmH
                                if (isSpoofingActive) {
                                    onUpdateMovement(0f, 0f, selectedSpeedKmH)
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = SpeedMode.entries.size),
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = mode.label,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        ) {
                            Text(
                                text = "${mode.label} (${mode.speedKmH.toInt()})",
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Custom Speed Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Speed: ${selectedSpeedKmH.toInt()} km/h",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(110.dp)
                    )
                    Slider(
                        value = selectedSpeedKmH,
                        onValueChange = { speed ->
                            selectedSpeedKmH = speed
                            if (isSpoofingActive) {
                                onUpdateMovement(0f, 0f, selectedSpeedKmH)
                            }
                        },
                        valueRange = 1f..120f,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Floating System Overlay Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Floating System Joystick Overlay",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = isOverlayRunning,
                        onCheckedChange = { enable ->
                            if (enable) {
                                if (Settings.canDrawOverlays(context)) {
                                    JoystickOverlayService.startOverlay(context)
                                } else {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            } else {
                                JoystickOverlayService.stopOverlay(context)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Main Toggle Action Button
                Button(
                    onClick = {
                        if (isSpoofingActive) {
                            onStopSpoofing()
                            JoystickOverlayService.stopOverlay(context)
                        } else {
                            onStartSpoofing(
                                selectedLocation.latitude,
                                selectedLocation.longitude,
                                selectedSpeedKmH
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSpoofingActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(
                        imageVector = if (isSpoofingActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSpoofingActive) "STOP SPOOFING" else "START SPOOFING",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
