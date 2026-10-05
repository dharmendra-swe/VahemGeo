package com.vahemgeo.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vahemgeo.app.data.local.BookmarkEntity
import com.vahemgeo.app.data.local.HistoryEntity
import com.vahemgeo.app.engine.GpxParser
import com.vahemgeo.app.engine.OsrmRoutingService
import com.vahemgeo.app.engine.SpeedMode
import com.vahemgeo.app.service.JoystickOverlayService
import com.vahemgeo.app.service.VahemMockService
import com.vahemgeo.app.ui.components.BookmarksSheet
import com.vahemgeo.app.ui.components.JoystickView
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun MapScreen(
    isSpoofingActive: Boolean,
    currentLocation: Pair<Double, Double>?,
    currentSpeed: Float,
    isOverlayRunning: Boolean,
    isJitterEnabled: Boolean,
    securityError: String?,
    bookmarks: List<BookmarkEntity>,
    historyLogs: List<HistoryEntity>,
    onStartSpoofing: (Double, Double, Float) -> Unit,
    onStartRouteSpoofing: (List<GeoPoint>, Float, Boolean) -> Unit,
    onStopSpoofing: () -> Unit,
    onUpdateLocation: (Double, Double) -> Unit,
    onUpdateMovement: (Float, Float, Float) -> Unit,
    onSetJitter: (Boolean) -> Unit,
    onAddBookmark: (String, Double, Double, String) -> Unit,
    onDeleteBookmark: (BookmarkEntity) -> Unit,
    onClearHistory: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedLocation by remember { mutableStateOf(GeoPoint(23.2599, 77.4126)) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var markerRef by remember { mutableStateOf<Marker?>(null) }
    var polylineRef by remember { mutableStateOf<Polyline?>(null) }

    var selectedSpeedKmH by remember { mutableFloatStateOf(SpeedMode.WALK.speedKmH) }
    var isRouteMode by remember { mutableStateOf(false) }
    var autoLoop by remember { mutableStateOf(true) }

    val waypoints = remember { mutableStateListOf<GeoPoint>() }
    var showBookmarksSheet by remember { mutableStateOf(false) }

    // GPX File Picker Launcher
    val gpxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            try {
                context.contentResolver.openInputStream(fileUri)?.use { stream ->
                    val parsedPoints = GpxParser.parseGpx(stream)
                    if (parsedPoints.isNotEmpty()) {
                        waypoints.clear()
                        waypoints.addAll(parsedPoints)
                        isRouteMode = true
                        selectedLocation = parsedPoints[0]
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Sync current location updates
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
                                if (isRouteMode) {
                                    waypoints.add(p)
                                } else {
                                    selectedLocation = p
                                    markerRef?.position = p
                                }
                                invalidate()
                            }
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean = false
                    })
                    overlays.add(eventsOverlay)

                    // Target Marker
                    val marker = Marker(this).apply {
                        position = selectedLocation
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Target Location"
                        snippet = "${selectedLocation.latitude}, ${selectedLocation.longitude}"
                    }
                    markerRef = marker
                    overlays.add(marker)

                    // Route Polyline
                    val polyline = Polyline(this).apply {
                        outlinePaint.color = android.graphics.Color.parseColor("#00B0FF")
                        outlinePaint.strokeWidth = 10f
                    }
                    polylineRef = polyline
                    overlays.add(polyline)

                    mapViewRef = this
                }
            },
            update = { mapView ->
                markerRef?.position = selectedLocation
                markerRef?.snippet = "${selectedLocation.latitude}, ${selectedLocation.longitude}"

                // Update polyline on map
                if (polylineRef != null) {
                    polylineRef?.setPoints(waypoints.toList())
                }
                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Status Header & Safe Area Controls
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Header Pill
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shadowElevation = 6.dp
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

                // Bookmarks / History Drawer Button
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(42.dp)
                ) {
                    IconButton(onClick = { showBookmarksSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Bookmarks & History",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Warning Card for Developer Options
            AnimatedVisibility(visible = securityError != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mock Location Not Enabled", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Select 'VahemGeo' in Developer Options", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                                } catch (_: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                }
                            }
                        ) {
                            Text("Open", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // On-Screen Joystick when Spoofing is Active
        AnimatedVisibility(
            visible = isSpoofingActive && !isRouteMode,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 340.dp)
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

        // Bottom Responsive Control Panel
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
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
                // Mode Selector: Point Spoof vs Route Simulator
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !isRouteMode,
                        onClick = { isRouteMode = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Default.AddLocation, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    ) {
                        Text("Point Mode", fontSize = 12.sp)
                    }
                    SegmentedButton(
                        selected = isRouteMode,
                        onClick = { isRouteMode = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    ) {
                        Text("Route Mode (${waypoints.size})", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Route Builder Actions Bar (Shown in Route Mode)
                AnimatedVisibility(visible = isRouteMode) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Tap map to add waypoints, or import GPX file:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Snap to Road (OSRM)
                            OutlinedButton(
                                onClick = {
                                    if (waypoints.size >= 2) {
                                        scope.launch {
                                            val roadPoints = OsrmRoutingService.fetchRoute(waypoints)
                                            waypoints.clear()
                                            waypoints.addAll(roadPoints)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Road Snap", fontSize = 11.sp)
                            }

                            // Import GPX
                            OutlinedButton(
                                onClick = { gpxLauncher.launch("*/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("GPX File", fontSize = 11.sp)
                            }

                            // Clear Route
                            IconButton(onClick = { waypoints.clear() }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Speed Presets Row
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
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
                            icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        ) {
                            Text("${mode.label} (${mode.speedKmH.toInt()})", fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

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

                // Micro-Jitter Stealth & Floating Overlay Switches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stealth Jitter", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = isJitterEnabled,
                        onCheckedChange = { onSetJitter(it) }
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Overlay", style = MaterialTheme.typography.bodySmall)
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

                Spacer(modifier = Modifier.height(10.dp))

                // Main Toggle Action Button
                Button(
                    onClick = {
                        if (isSpoofingActive) {
                            onStopSpoofing()
                            JoystickOverlayService.stopOverlay(context)
                        } else {
                            if (isRouteMode && waypoints.isNotEmpty()) {
                                onStartRouteSpoofing(waypoints.toList(), selectedSpeedKmH, autoLoop)
                            } else {
                                onStartSpoofing(selectedLocation.latitude, selectedLocation.longitude, selectedSpeedKmH)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSpoofingActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = if (isSpoofingActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSpoofingActive) "STOP SPOOFING"
                        else if (isRouteMode) "START ROUTE SIMULATION"
                        else "START SPOOFING",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Bookmarks & History Bottom Sheet Modal
        if (showBookmarksSheet) {
            BookmarksSheet(
                bookmarks = bookmarks,
                historyLogs = historyLogs,
                currentLat = selectedLocation.latitude,
                currentLng = selectedLocation.longitude,
                onDismiss = { showBookmarksSheet = false },
                onSelectLocation = { lat, lng ->
                    selectedLocation = GeoPoint(lat, lng)
                    markerRef?.position = selectedLocation
                    mapViewRef?.controller?.animateTo(selectedLocation)
                    mapViewRef?.invalidate()
                },
                onAddBookmark = onAddBookmark,
                onDeleteBookmark = onDeleteBookmark,
                onClearHistory = onClearHistory
            )
        }
    }
}
