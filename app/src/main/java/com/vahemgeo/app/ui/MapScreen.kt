package com.vahemgeo.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.provider.Settings
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vahemgeo.app.data.local.BookmarkEntity
import com.vahemgeo.app.data.local.HistoryEntity
import com.vahemgeo.app.engine.AntiLeakShieldManager
import com.vahemgeo.app.engine.GpxParser
import com.vahemgeo.app.engine.LocationSearchService
import com.vahemgeo.app.engine.OsrmRoutingService
import com.vahemgeo.app.engine.SearchLocationResult
import com.vahemgeo.app.engine.SpeedMode
import com.vahemgeo.app.service.JoystickOverlayService
import com.vahemgeo.app.ui.components.BookmarksSheet
import com.vahemgeo.app.ui.components.JoystickView
import com.vahemgeo.app.ui.components.SecurityShieldSheet
import com.vahemgeo.app.ui.theme.CyberTeal
import com.vahemgeo.app.ui.theme.DarkCanvas
import com.vahemgeo.app.ui.theme.ElectricLime
import com.vahemgeo.app.ui.theme.MutedSage
import com.vahemgeo.app.ui.theme.PolarWhite
import com.vahemgeo.app.ui.theme.SurfaceCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

private enum class SearchTargetField {
    SINGLE_POINT,
    ROUTE_FROM,
    ROUTE_TO
}

@SuppressLint("ClickableViewAccessibility")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapScreen(
    hasLocationPermission: Boolean,
    isSpoofingActive: Boolean,
    currentLocation: Pair<Double, Double>?,
    currentSpeed: Float,
    routeProgress: Float,
    remainingDistanceMeters: Double,
    totalRouteDistanceMeters: Double,
    isRoutePaused: Boolean,
    isOverlayRunning: Boolean,
    isJitterEnabled: Boolean,
    isAutoWalkEnabled: Boolean,
    securityError: String?,
    bookmarks: List<BookmarkEntity>,
    historyLogs: List<HistoryEntity>,
    onStartSpoofing: (Double, Double, Float, String) -> Unit,
    onStartRouteSpoofing: (List<GeoPoint>, Float, Boolean, String) -> Unit,
    onStopSpoofing: () -> Unit,
    onUpdateLocation: (Double, Double) -> Unit,
    onUpdateMovement: (Float, Float, Float) -> Unit,
    onSetJitter: (Boolean) -> Unit,
    onSetAutoWalk: (Boolean) -> Unit,
    onSetRoutePaused: (Boolean) -> Unit,
    onSetAutoLoop: (Boolean) -> Unit,
    onAddBookmark: (String, Double, Double, String) -> Unit,
    onDeleteBookmark: (BookmarkEntity) -> Unit,
    onClearHistory: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Default initial location: saved real/spoofed location or fallback until GPS fix arrives
    val initialGeo = remember {
        AntiLeakShieldManager.getSavedRealLocation(context)
            ?: AntiLeakShieldManager.getLastSpoofedLocation(context)
            ?: AntiLeakShieldManager.DEFAULT_FALLBACK_POINT
    }

    var selectedLocation by remember { mutableStateOf(initialGeo) }
    var selectedLocationLabel by remember { mutableStateOf("Locating current position...") }
    var isLocatingCurrentGps by remember { mutableStateOf(false) }
    var hasInitializedCurrentLocation by remember { mutableStateOf(false) }

    // Camera Follow vs Free-Pan Mode (prevents camera yank during 10Hz spoofing)
    var isFollowingLiveLocation by remember { mutableStateOf(true) }
    var lastCameraAnimatedGeo by remember { mutableStateOf<GeoPoint?>(null) }

    // Anti-Teleport Cooldown Guard State
    var pendingTeleportTarget by remember { mutableStateOf<Pair<GeoPoint, String>?>(null) }

    // Map & Overlays references
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var targetMarkerRef by remember { mutableStateOf<Marker?>(null) }
    var startMarkerRef by remember { mutableStateOf<Marker?>(null) }
    var endMarkerRef by remember { mutableStateOf<Marker?>(null) }
    var polylineRef by remember { mutableStateOf<Polyline?>(null) }

    // Mode & Speed states
    var selectedSpeedKmH by remember { mutableFloatStateOf(SpeedMode.WALK.speedKmH) }
    var isRouteMode by remember { mutableStateOf(false) }
    var autoLoop by remember { mutableStateOf(false) }
    var isPanelExpanded by remember { mutableStateOf(true) }

    // Route Planner (From A -> To B) States
    var routeStartPoint by remember { mutableStateOf<GeoPoint?>(initialGeo) }
    var routeStartLabel by remember { mutableStateOf("Current Location") }
    var routeEndPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var routeEndLabel by remember { mutableStateOf("Tap map or search destination") }
    var activeMapTapTarget by remember { mutableStateOf(SearchTargetField.ROUTE_TO) }
    var isCalculatingRoute by remember { mutableStateOf(false) }
    var plannedRouteDistanceMeters by remember { mutableDoubleStateOf(0.0) }
    val waypoints = remember { mutableStateListOf<GeoPoint>() }

    // Search Bar & Autocomplete States
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchTargetField by remember { mutableStateOf(SearchTargetField.SINGLE_POINT) }
    var isSearchingNetwork by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SearchLocationResult>>(emptyList()) }
    var searchDebounceJob by remember { mutableStateOf<Job?>(null) }

    // Bottom Sheets
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showSecuritySheet by remember { mutableStateOf(false) }
    var auditRefreshTrigger by remember { mutableStateOf(0) }

    val auditStatus = remember(isSpoofingActive, isJitterEnabled, auditRefreshTrigger) {
        AntiLeakShieldManager.auditSecurityStatus(context, isSpoofingActive, isJitterEnabled)
    }

    // Helper to calculate route between routeStartPoint (A) and routeEndPoint (B)
    fun recalculateABRoute(animateToFit: Boolean = true, autoStartAfterCalc: Boolean = false) {
        val start = routeStartPoint ?: return
        val end = routeEndPoint ?: return
        scope.launch {
            isCalculatingRoute = true
            val result = OsrmRoutingService.calculateDetailedRoute(listOf(start, end))
            waypoints.clear()
            waypoints.addAll(result.points)
            plannedRouteDistanceMeters = result.totalDistanceMeters
            isCalculatingRoute = false

            if (animateToFit && result.points.size >= 2) {
                isFollowingLiveLocation = false
                try {
                    val bbox = BoundingBox.fromGeoPoints(result.points)
                    mapViewRef?.post {
                        try {
                            mapViewRef?.zoomToBoundingBox(bbox, true, 120)
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }

            if (autoStartAfterCalc && result.points.size >= 2) {
                isFollowingLiveLocation = true
                onStartRouteSpoofing(
                    result.points,
                    selectedSpeedKmH,
                    autoLoop,
                    "$routeStartLabel ➔ $routeEndLabel"
                )
            }
        }
    }

    // Helper to safely handle a target location selection (with Anti-Teleport Guard when spoofing is active)
    fun handleSinglePointSelection(targetPt: GeoPoint, label: String?) {
        if (isSpoofingActive) {
            val distMeters = selectedLocation.distanceToAsDouble(targetPt)
            if (distMeters > 500.0) {
                // Trigger Anti-Teleport Guard for jumps > 500 meters while spoofing is live
                pendingTeleportTarget = Pair(
                    targetPt,
                    label ?: "${"%.4f".format(Locale.US, targetPt.latitude)}, ${"%.4f".format(Locale.US, targetPt.longitude)}"
                )
                return
            }
            selectedLocation = targetPt
            onUpdateLocation(targetPt.latitude, targetPt.longitude)
        } else {
            selectedLocation = targetPt
        }

        if (label != null) {
            selectedLocationLabel = if (isSpoofingActive) "Spoofed • $label" else label
        } else {
            selectedLocationLabel = "${"%.5f".format(Locale.US, targetPt.latitude)}, ${"%.5f".format(Locale.US, targetPt.longitude)}"
            scope.launch {
                val resolved = LocationSearchService.reverseGeocode(targetPt.latitude, targetPt.longitude)
                selectedLocationLabel = if (isSpoofingActive) "Spoofed • $resolved" else resolved
            }
        }
    }

    // Helper to fetch and select device's real/current location
    fun locateAndSelectCurrentPosition(setAsRouteStartToo: Boolean = false) {
        scope.launch {
            isLocatingCurrentGps = true
            isFollowingLiveLocation = true
            val currentPt = AntiLeakShieldManager.fetchCurrentDeviceLocation(context, isSpoofingActive)
            if (currentPt != null) {
                selectedLocation = currentPt
                lastCameraAnimatedGeo = currentPt
                if (setAsRouteStartToo || routeStartPoint == null) {
                    routeStartPoint = currentPt
                    routeStartLabel = "Current Location (${"%.4f".format(Locale.US, currentPt.latitude)}, ${"%.4f".format(Locale.US, currentPt.longitude)})"
                }
                mapViewRef?.controller?.animateTo(currentPt)
                mapViewRef?.controller?.setZoom(16.5)
                mapViewRef?.invalidate()

                val placeName = LocationSearchService.reverseGeocode(currentPt.latitude, currentPt.longitude)
                selectedLocationLabel = if (isSpoofingActive) {
                    "Spoofed • $placeName"
                } else {
                    "Current • $placeName"
                }
                if (setAsRouteStartToo) {
                    routeStartLabel = placeName
                }
            } else {
                selectedLocationLabel = "${"%.5f".format(Locale.US, selectedLocation.latitude)}, ${"%.5f".format(Locale.US, selectedLocation.longitude)}"
            }
            isLocatingCurrentGps = false
        }
    }

    // Auto-select Current Location on startup and when location permission is granted
    LaunchedEffect(hasLocationPermission) {
        if (!hasInitializedCurrentLocation || hasLocationPermission) {
            hasInitializedCurrentLocation = true
            locateAndSelectCurrentPosition(setAsRouteStartToo = true)
        }
    }

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
                        routeStartPoint = parsedPoints.first()
                        routeStartLabel = "GPX Start (${"%.4f".format(Locale.US, parsedPoints.first().latitude)}, ${"%.4f".format(Locale.US, parsedPoints.first().longitude)})"
                        routeEndPoint = parsedPoints.last()
                        routeEndLabel = "GPX End (${"%.4f".format(Locale.US, parsedPoints.last().latitude)}, ${"%.4f".format(Locale.US, parsedPoints.last().longitude)})"
                        plannedRouteDistanceMeters = OsrmRoutingService.computeTotalDistanceMeters(parsedPoints)
                        selectedLocation = parsedPoints.first()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Sync live spoofed location updates onto the map WITHOUT locking user camera panning!
    LaunchedEffect(currentLocation, isSpoofingActive) {
        if (isSpoofingActive && currentLocation != null) {
            val point = GeoPoint(currentLocation.first, currentLocation.second)
            selectedLocation = point
            targetMarkerRef?.position = point

            // Only animate camera if user hasn't manually panned away AND moved >= 2.5m (ignores sub-meter jitter)
            if (isFollowingLiveLocation) {
                val prevCam = lastCameraAnimatedGeo
                if (prevCam == null || prevCam.distanceToAsDouble(point) >= 2.5) {
                    lastCameraAnimatedGeo = point
                    mapViewRef?.controller?.animateTo(point)
                }
            }
            mapViewRef?.invalidate()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapViewRef?.onDetach()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val screenWidth = maxWidth
        val isCompactScreen = screenHeight < 700.dp || screenWidth < 360.dp

        // 1. Full-Screen Interactive OSM MapView
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    setScrollableAreaLimitDouble(BoundingBox(85.0, 180.0, -85.0, -180.0))
                    minZoomLevel = 3.0
                    maxZoomLevel = 20.0
                    controller.setZoom(16.0)
                    controller.setCenter(selectedLocation)

                    // Detect manual user finger drag on map to unlock camera follow during spoofing
                    var downX = 0f
                    var downY = 0f
                    setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                downX = event.x
                                downY = event.y
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (hypot((event.x - downX).toDouble(), (event.y - downY).toDouble()) > 24.0) {
                                    isFollowingLiveLocation = false
                                }
                            }
                        }
                        false
                    }

                    val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            focusManager.clearFocus()
                            isSearchActive = false

                            if (isRouteMode && !isSpoofingActive) {
                                if (activeMapTapTarget == SearchTargetField.ROUTE_FROM) {
                                    routeStartPoint = p
                                    routeStartLabel = "${"%.4f".format(Locale.US, p.latitude)}, ${"%.4f".format(Locale.US, p.longitude)}"
                                    selectedLocation = p
                                    activeMapTapTarget = SearchTargetField.ROUTE_TO
                                    scope.launch {
                                        routeStartLabel = LocationSearchService.reverseGeocode(p.latitude, p.longitude)
                                    }
                                    if (routeEndPoint != null) {
                                        recalculateABRoute(animateToFit = false)
                                    }
                                } else {
                                    routeEndPoint = p
                                    routeEndLabel = "${"%.4f".format(Locale.US, p.latitude)}, ${"%.4f".format(Locale.US, p.longitude)}"
                                    scope.launch {
                                        routeEndLabel = LocationSearchService.reverseGeocode(p.latitude, p.longitude)
                                    }
                                    if (routeStartPoint == null) {
                                        routeStartPoint = selectedLocation
                                    }
                                    recalculateABRoute(animateToFit = false)
                                }
                            } else {
                                handleSinglePointSelection(p, null)
                            }
                            invalidate()
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean = false
                    })
                    overlays.add(eventsOverlay)

                    // Route Polyline
                    val polyline = Polyline(this).apply {
                        outlinePaint.color = android.graphics.Color.parseColor("#00E5FF")
                        outlinePaint.strokeWidth = 12f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.isAntiAlias = true
                    }
                    polylineRef = polyline
                    overlays.add(polyline)

                    // Start Marker (Point A)
                    val startMarker = Marker(this).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "START (Point A)"
                        isEnabled = false
                    }
                    startMarkerRef = startMarker
                    overlays.add(startMarker)

                    // Destination Marker (Point B)
                    val endMarker = Marker(this).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "DESTINATION (Point B)"
                        isEnabled = false
                    }
                    endMarkerRef = endMarker
                    overlays.add(endMarker)

                    // Active / Current Selected Marker
                    val targetMarker = Marker(this).apply {
                        position = selectedLocation
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Active Location"
                        snippet = "${selectedLocation.latitude}, ${selectedLocation.longitude}"
                    }
                    targetMarkerRef = targetMarker
                    overlays.add(targetMarker)

                    mapViewRef = this
                }
            },
            update = { mapView ->
                targetMarkerRef?.position = selectedLocation
                targetMarkerRef?.title = if (isSpoofingActive) "LIVE SPOOFED POSITION" else "Selected Location"
                targetMarkerRef?.snippet = "${"%.5f".format(Locale.US, selectedLocation.latitude)}, ${"%.5f".format(Locale.US, selectedLocation.longitude)}"

                // Update Start (A) and Destination (B) markers in Route Mode
                if (isRouteMode) {
                    val sPt = routeStartPoint
                    if (sPt != null) {
                        startMarkerRef?.isEnabled = true
                        startMarkerRef?.position = sPt
                        startMarkerRef?.title = "FROM (A): $routeStartLabel"
                    } else {
                        startMarkerRef?.isEnabled = false
                    }

                    val ePt = routeEndPoint
                    if (ePt != null) {
                        endMarkerRef?.isEnabled = true
                        endMarkerRef?.position = ePt
                        endMarkerRef?.title = "TO (B): $routeEndLabel"
                    } else {
                        endMarkerRef?.isEnabled = false
                    }

                    polylineRef?.setPoints(waypoints.toList())
                } else {
                    startMarkerRef?.isEnabled = false
                    endMarkerRef?.isEnabled = false
                    polylineRef?.setPoints(emptyList())
                }

                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Responsive Foreground UI Layer (Top Bar + Middle Non-Overlapping Controls + Bottom Sheet)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ================= TOP SECTION: STATUS PILLS + SEARCH BAR =================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Status Row: Spoofing Status + 99% Anti-Leak Shield + Bookmarks
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status Pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SurfaceCard.copy(alpha = 0.95f),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .background(
                                        color = if (isSpoofingActive) ElectricLime else Color(0xFFFFB300),
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSpoofingActive) "SPOOFING LIVE" else "READY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PolarWhite
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 99% Anti-Leak Shield Button
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = CyberTeal.copy(alpha = 0.95f),
                            shadowElevation = 6.dp,
                            modifier = Modifier.clickable {
                                auditRefreshTrigger++
                                showSecuritySheet = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Anti-Leak Shield",
                                    tint = ElectricLime,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Shield ${auditStatus.safetyScorePercent}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricLime
                                )
                            }
                        }

                        // Bookmarks & History Button
                        Surface(
                            shape = CircleShape,
                            color = SurfaceCard.copy(alpha = 0.95f),
                            shadowElevation = 6.dp,
                            modifier = Modifier.size(36.dp)
                        ) {
                            IconButton(onClick = { showBookmarksSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = "Bookmarks & History",
                                    tint = ElectricLime,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Universal Location Search Bar (Search Any Place, City, or Coordinates)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard.copy(alpha = 0.96f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { query ->
                                searchQuery = query
                                isSearchActive = true
                                searchDebounceJob?.cancel()
                                if (query.trim().length >= 2) {
                                    searchDebounceJob = scope.launch {
                                        delay(350L)
                                        isSearchingNetwork = true
                                        searchResults = LocationSearchService.searchLocations(query)
                                        isSearchingNetwork = false
                                    }
                                } else {
                                    searchResults = emptyList()
                                    isSearchingNetwork = false
                                }
                            },
                            placeholder = {
                                val hint = when {
                                    isRouteMode && searchTargetField == SearchTargetField.ROUTE_FROM ->
                                        "Search Start (A) place or lat, lng..."
                                    isRouteMode && searchTargetField == SearchTargetField.ROUTE_TO ->
                                        "Search Destination (B) place or lat, lng..."
                                    else -> "Search any location, city, or lat,lng..."
                                }
                                Text(hint, fontSize = 12.sp, color = MutedSage, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = ElectricLime, modifier = Modifier.size(20.dp))
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSearchingNetwork) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = ElectricLime
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    if (searchQuery.isNotEmpty() || isSearchActive) {
                                        IconButton(
                                            onClick = {
                                                searchQuery = ""
                                                searchResults = emptyList()
                                                isSearchActive = false
                                                focusManager.clearFocus()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = MutedSage, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = PolarWhite, fontSize = 13.sp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    scope.launch {
                                        isSearchingNetwork = true
                                        searchResults = LocationSearchService.searchLocations(searchQuery)
                                        isSearchingNetwork = false
                                    }
                                    focusManager.clearFocus()
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricLime,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        )

                        // Search Autocomplete Results Dropdown
                        AnimatedVisibility(visible = isSearchActive && searchResults.isNotEmpty()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                HorizontalDivider(color = CyberTeal)
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 210.dp)
                                ) {
                                    items(searchResults) { item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val pt = GeoPoint(item.latitude, item.longitude)
                                                    if (isRouteMode) {
                                                        if (searchTargetField == SearchTargetField.ROUTE_FROM) {
                                                            routeStartPoint = pt
                                                            routeStartLabel = item.title
                                                            selectedLocation = pt
                                                        } else {
                                                            routeEndPoint = pt
                                                            routeEndLabel = item.title
                                                            if (routeStartPoint == null) {
                                                                routeStartPoint = selectedLocation
                                                            }
                                                        }
                                                        recalculateABRoute(animateToFit = true)
                                                    } else {
                                                        mapViewRef?.controller?.animateTo(pt)
                                                        mapViewRef?.controller?.setZoom(16.5)
                                                        handleSinglePointSelection(pt, "${item.title} (${item.subtitle})")
                                                    }
                                                    searchQuery = ""
                                                    searchResults = emptyList()
                                                    isSearchActive = false
                                                    focusManager.clearFocus()
                                                }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Place, contentDescription = null, tint = ElectricLime, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PolarWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(item.subtitle, fontSize = 11.sp, color = MutedSage, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Developer Options Warning Banner (if Mock Location App not set)
                AnimatedVisibility(visible = securityError != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Mock Location / Permission Alert", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text(securityError ?: "Select 'VahemGeo' in Developer Options", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                                    } catch (_: Exception) {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Enable", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // ================= MIDDLE SECTION: NON-OVERLAPPING JOYSTICK & MAP FABS =================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                // On-Screen Joystick (Bottom-Start of Middle Area — Never overlaps bottom card!)
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSpoofingActive && !isRouteMode,
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    val joystickDiameter = if (isCompactScreen) 106.dp else 124.dp
                    val knobDiameter = if (isCompactScreen) 40.dp else 46.dp
                    JoystickView(
                        size = joystickDiameter,
                        knobSize = knobDiameter,
                        onJoystickMoved = { bearing, intensity ->
                            onUpdateMovement(bearing, intensity, selectedSpeedKmH)
                        },
                        onJoystickReleased = {
                            onUpdateMovement(0f, 0f, selectedSpeedKmH)
                        }
                    )
                }

                // Right-Side Floating Map Buttons (My Current Location / Re-Center Follow + Fit Route)
                Column(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isRouteMode && waypoints.size >= 2) {
                        SmallFloatingActionButton(
                            onClick = {
                                isFollowingLiveLocation = false
                                try {
                                    val bbox = BoundingBox.fromGeoPoints(waypoints)
                                    mapViewRef?.zoomToBoundingBox(bbox, true, 120)
                                } catch (_: Exception) {}
                            },
                            containerColor = SurfaceCard,
                            contentColor = ElectricLime,
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Directions, contentDescription = "Fit Route", modifier = Modifier.size(18.dp))
                        }
                    }

                    FloatingActionButton(
                        onClick = {
                            isFollowingLiveLocation = true
                            if (isSpoofingActive) {
                                lastCameraAnimatedGeo = selectedLocation
                                mapViewRef?.controller?.animateTo(selectedLocation)
                            } else {
                                locateAndSelectCurrentPosition(setAsRouteStartToo = false)
                            }
                        },
                        containerColor = if (isFollowingLiveLocation) SurfaceCard else CyberTeal,
                        contentColor = ElectricLime,
                        shape = CircleShape,
                        modifier = Modifier.size(46.dp)
                    ) {
                        if (isLocatingCurrentGps) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = ElectricLime
                            )
                        } else {
                            Icon(
                                imageVector = if (isFollowingLiveLocation) Icons.Default.MyLocation else Icons.Default.GpsFixed,
                                contentDescription = "My Current Location / Follow",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // ================= BOTTOM SECTION: RESPONSIVE COLLAPSIBLE CONTROL PANEL =================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = SurfaceCard.copy(alpha = 0.96f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // Top Handle Row: Current Selected Location / Route Summary + Collapse/Expand Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isPanelExpanded = !isPanelExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isRouteMode) Icons.Default.Directions else Icons.Default.GpsFixed,
                                contentDescription = null,
                                tint = ElectricLime,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isRouteMode) {
                                        "$routeStartLabel ➔ $routeEndLabel"
                                    } else {
                                        selectedLocationLabel
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = PolarWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${"%.5f".format(Locale.US, selectedLocation.latitude)}, ${"%.5f".format(Locale.US, selectedLocation.longitude)} • ${selectedSpeedKmH.toInt()} km/h",
                                    fontSize = 11.sp,
                                    color = MutedSage,
                                    maxLines = 1
                                )
                            }
                        }

                        IconButton(
                            onClick = { isPanelExpanded = !isPanelExpanded },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = if (isPanelExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                contentDescription = if (isPanelExpanded) "Minimize Panel" else "Expand Panel",
                                tint = ElectricLime
                            )
                        }
                    }

                    // Live Route Progress Bar (Always visible when Route Mode has a planned/active route)
                    val displayTotalDist = if (isSpoofingActive && totalRouteDistanceMeters > 0) {
                        totalRouteDistanceMeters
                    } else {
                        plannedRouteDistanceMeters
                    }
                    val displayRemainingDist = if (isSpoofingActive && isRouteMode) {
                        remainingDistanceMeters
                    } else {
                        plannedRouteDistanceMeters
                    }

                    if (isRouteMode && displayTotalDist > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val etaMinutes = ((displayRemainingDist / 1000.0) / selectedSpeedKmH.coerceAtLeast(1f) * 60.0).roundToInt()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isSpoofingActive) {
                                    "Left: ${formatDistance(displayRemainingDist)} / ${formatDistance(displayTotalDist)}"
                                } else {
                                    "Route Distance: ${formatDistance(displayTotalDist)}"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ElectricLime
                            )
                            Text(
                                text = "ETA: ~${if (etaMinutes < 1) "<1 min" else "$etaMinutes min"} (${(routeProgress * 100).toInt()}%)",
                                fontSize = 11.sp,
                                color = PolarWhite
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { if (isSpoofingActive) routeProgress else 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = ElectricLime,
                            trackColor = CyberTeal
                        )
                    }

                    // Expandable Detailed Controls Section (Responsive max-height with scroll)
                    AnimatedVisibility(visible = isPanelExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = screenHeight * 0.44f)
                                .verticalScroll(rememberScrollState())
                                .padding(top = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Mode Selector: Single Point Spoof vs A -> B Route Movement
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = !isRouteMode,
                                    onClick = {
                                        isRouteMode = false
                                        searchTargetField = SearchTargetField.SINGLE_POINT
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                    icon = { Icon(Icons.Default.AddLocation, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                ) {
                                    Text("Single Point", fontSize = 11.sp, maxLines = 1)
                                }
                                SegmentedButton(
                                    selected = isRouteMode,
                                    onClick = {
                                        isRouteMode = true
                                        if (routeStartPoint == null) {
                                            routeStartPoint = selectedLocation
                                            routeStartLabel = selectedLocationLabel
                                        }
                                        searchTargetField = SearchTargetField.ROUTE_TO
                                        activeMapTapTarget = SearchTargetField.ROUTE_TO
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                    icon = { Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                ) {
                                    Text("Move A ➔ B Route", fontSize = 11.sp, maxLines = 1)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // ================= FROM (A) -> TO (B) ROUTE PLANNER UI =================
                            AnimatedVisibility(visible = isRouteMode) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CyberTeal.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // FROM (Point A - Kaha Se)
                                            val isSelectingFrom = activeMapTapTarget == SearchTargetField.ROUTE_FROM
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (isSelectingFrom) CyberTeal else DarkCanvas.copy(alpha = 0.7f))
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isSelectingFrom) ElectricLime else Color.Transparent,
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .clickable {
                                                        activeMapTapTarget = SearchTargetField.ROUTE_FROM
                                                        searchTargetField = SearchTargetField.ROUTE_FROM
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .background(ElectricLime, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("A", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = DarkCanvas)
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("FROM (Kaha Se)", fontSize = 9.sp, color = ElectricLime, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        text = routeStartLabel,
                                                        fontSize = 11.sp,
                                                        color = PolarWhite,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { locateAndSelectCurrentPosition(setAsRouteStartToo = true) },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(Icons.Default.MyLocation, contentDescription = "Use Current GPS", tint = ElectricLime, modifier = Modifier.size(15.dp))
                                                }
                                            }

                                            // TO (Point B - Kaha Tak)
                                            val isSelectingTo = activeMapTapTarget == SearchTargetField.ROUTE_TO
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (isSelectingTo) CyberTeal else DarkCanvas.copy(alpha = 0.7f))
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isSelectingTo) ElectricLime else Color.Transparent,
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .clickable {
                                                        activeMapTapTarget = SearchTargetField.ROUTE_TO
                                                        searchTargetField = SearchTargetField.ROUTE_TO
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .background(Color(0xFFFF5252), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("B", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = PolarWhite)
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("TO (Kaha Tak - Tap Map or Search Above)", fontSize = 9.sp, color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
                                                    Text(
                                                        text = routeEndLabel,
                                                        fontSize = 11.sp,
                                                        color = if (routeEndPoint != null) PolarWhite else MutedSage,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Icon(Icons.Default.Flag, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // Swap A <-> B Button
                                        IconButton(
                                            onClick = {
                                                val tempPt = routeStartPoint
                                                val tempLbl = routeStartLabel
                                                routeStartPoint = routeEndPoint
                                                routeStartLabel = routeEndLabel
                                                routeEndPoint = tempPt
                                                routeEndLabel = tempLbl
                                                recalculateABRoute(animateToFit = true)
                                            },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(SurfaceCard, CircleShape)
                                        ) {
                                            Icon(Icons.Default.SwapVert, contentDescription = "Swap From and To", tint = ElectricLime, modifier = Modifier.size(18.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Route Action Buttons: Road Snap / GPX / Clear
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = { recalculateABRoute(animateToFit = true) },
                                            enabled = routeStartPoint != null && routeEndPoint != null && !isCalculatingRoute,
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(34.dp)
                                        ) {
                                            if (isCalculatingRoute) {
                                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = ElectricLime)
                                            } else {
                                                Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(14.dp))
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Road Route", fontSize = 11.sp, maxLines = 1)
                                        }

                                        OutlinedButton(
                                            onClick = { gpxLauncher.launch("*/*") },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(34.dp)
                                        ) {
                                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Import GPX", fontSize = 11.sp, maxLines = 1)
                                        }

                                        IconButton(
                                            onClick = {
                                                waypoints.clear()
                                                routeEndPoint = null
                                                routeEndLabel = "Tap map or search destination"
                                                plannedRouteDistanceMeters = 0.0
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear Route", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Speed Presets Row
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SpeedMode.entries.forEachIndexed { index, mode ->
                                    val icon = when (mode) {
                                        SpeedMode.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
                                        SpeedMode.CYCLE -> Icons.AutoMirrored.Filled.DirectionsBike
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
                                        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    ) {
                                        Text("${mode.label} (${mode.speedKmH.toInt()})", fontSize = 10.sp, maxLines = 1)
                                    }
                                }
                            }

                            // Custom Speed Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Speed: ${selectedSpeedKmH.toInt()} km/h",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PolarWhite,
                                    modifier = Modifier.width(96.dp)
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

                            // Responsive FlowRow of Feature Chips (Works on ALL screen widths without clipping!)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FilterChip(
                                    selected = isJitterEnabled,
                                    onClick = { onSetJitter(!isJitterEnabled) },
                                    label = { Text("Stealth Jitter", fontSize = 10.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(14.dp))
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyberTeal,
                                        selectedLabelColor = ElectricLime,
                                        selectedLeadingIconColor = ElectricLime
                                    )
                                )

                                if (!isRouteMode) {
                                    FilterChip(
                                        selected = isAutoWalkEnabled,
                                        onClick = { onSetAutoWalk(!isAutoWalkEnabled) },
                                        label = { Text("Auto-Walk", fontSize = 10.sp) },
                                        leadingIcon = {
                                            Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, modifier = Modifier.size(14.dp))
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CyberTeal,
                                            selectedLabelColor = ElectricLime,
                                            selectedLeadingIconColor = ElectricLime
                                        )
                                    )
                                } else {
                                    FilterChip(
                                        selected = autoLoop,
                                        onClick = {
                                            autoLoop = !autoLoop
                                            if (isSpoofingActive) onSetAutoLoop(autoLoop)
                                        },
                                        label = { Text("Loop A⇄B", fontSize = 10.sp) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Loop, contentDescription = null, modifier = Modifier.size(14.dp))
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CyberTeal,
                                            selectedLabelColor = ElectricLime,
                                            selectedLeadingIconColor = ElectricLime
                                        )
                                    )
                                }

                                FilterChip(
                                    selected = isOverlayRunning,
                                    onClick = {
                                        if (!isOverlayRunning) {
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
                                    },
                                    label = { Text("Floating Joystick", fontSize = 10.sp) },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyberTeal,
                                        selectedLabelColor = ElectricLime,
                                        selectedLeadingIconColor = ElectricLime
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Primary Action Row (Start / Pause-Resume / Stop)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSpoofingActive && isRouteMode) {
                            OutlinedButton(
                                onClick = { onSetRoutePaused(!isRoutePaused) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(0.45f)
                                    .height(44.dp)
                            ) {
                                Icon(
                                    imageVector = if (isRoutePaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isRoutePaused) "Resume" else "Pause", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = {
                                if (isSpoofingActive) {
                                    onStopSpoofing()
                                    JoystickOverlayService.stopOverlay(context)
                                } else {
                                    isFollowingLiveLocation = true
                                    if (isRouteMode) {
                                        val pointsToUse = when {
                                            waypoints.size >= 2 -> waypoints.toList()
                                            routeStartPoint != null && routeEndPoint != null ->
                                                listOf(routeStartPoint!!, routeEndPoint!!)
                                            else -> emptyList()
                                        }
                                        if (pointsToUse.size >= 2) {
                                            onStartRouteSpoofing(
                                                pointsToUse,
                                                selectedSpeedKmH,
                                                autoLoop,
                                                "$routeStartLabel ➔ $routeEndLabel"
                                            )
                                        } else {
                                            // If destination B is not picked yet, prompt by focusing To (B)
                                            activeMapTapTarget = SearchTargetField.ROUTE_TO
                                            searchTargetField = SearchTargetField.ROUTE_TO
                                            isPanelExpanded = true
                                        }
                                    } else {
                                        onStartSpoofing(
                                            selectedLocation.latitude,
                                            selectedLocation.longitude,
                                            selectedSpeedKmH,
                                            selectedLocationLabel
                                        )
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSpoofingActive) MaterialTheme.colorScheme.error else ElectricLime
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(
                                imageVector = if (isSpoofingActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val btnText = when {
                                isSpoofingActive -> "STOP SPOOFING"
                                isRouteMode && routeEndPoint == null && waypoints.size < 2 -> "SELECT DESTINATION (B)"
                                isRouteMode -> "START MOVE (A ➔ B)"
                                else -> "START SPOOFING HERE"
                            }
                            Text(
                                text = btnText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Anti-Teleport Cooldown Guard Dialog (Prevents accidental 500km instant jump while spoofing is live)
        pendingTeleportTarget?.let { (targetPt, targetLabel) ->
            val jumpDist = selectedLocation.distanceToAsDouble(targetPt)
            AlertDialog(
                onDismissRequest = { pendingTeleportTarget = null },
                containerColor = SurfaceCard,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = ElectricLime)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Anti-Teleport Guard", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PolarWhite)
                    }
                },
                text = {
                    Text(
                        text = "You are currently spoofing live and tapped a point ${formatDistance(jumpDist)} away.\n\nInstant teleporting long distances while spoofing is active can trigger anti-cheat detection. Would you like to travel smoothly along a road route instead?",
                        fontSize = 13.sp,
                        color = MutedSage
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            routeStartPoint = selectedLocation
                            routeStartLabel = selectedLocationLabel
                            routeEndPoint = targetPt
                            routeEndLabel = targetLabel
                            isRouteMode = true
                            pendingTeleportTarget = null
                            recalculateABRoute(animateToFit = true, autoStartAfterCalc = true)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricLime)
                    ) {
                        Text("Move Smoothly (A ➔ B)", color = DarkCanvas, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { pendingTeleportTarget = null }) {
                            Text("Cancel", color = MutedSage)
                        }
                        TextButton(
                            onClick = {
                                pendingTeleportTarget = null
                                selectedLocation = targetPt
                                selectedLocationLabel = "Spoofed • $targetLabel"
                                onUpdateLocation(targetPt.latitude, targetPt.longitude)
                            }
                        ) {
                            Text("Teleport Anyway", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
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
                    val pt = GeoPoint(lat, lng)
                    if (isRouteMode && !isSpoofingActive) {
                        if (activeMapTapTarget == SearchTargetField.ROUTE_FROM) {
                            routeStartPoint = pt
                            scope.launch { routeStartLabel = LocationSearchService.reverseGeocode(lat, lng) }
                        } else {
                            routeEndPoint = pt
                            scope.launch { routeEndLabel = LocationSearchService.reverseGeocode(lat, lng) }
                        }
                        recalculateABRoute(animateToFit = true)
                    } else {
                        mapViewRef?.controller?.animateTo(pt)
                        mapViewRef?.invalidate()
                        handleSinglePointSelection(pt, null)
                    }
                },
                onAddBookmark = onAddBookmark,
                onDeleteBookmark = onDeleteBookmark,
                onClearHistory = onClearHistory
            )
        }

        // 99% Anti-Leak Security Shield Sheet
        if (showSecuritySheet) {
            SecurityShieldSheet(
                auditStatus = auditStatus,
                onToggleCoordinateLock = { enabled ->
                    AntiLeakShieldManager.setCoordinateLockEnabled(context, enabled)
                    auditRefreshTrigger++
                },
                onToggleJitter = { enabled ->
                    onSetJitter(enabled)
                    auditRefreshTrigger++
                },
                onRefreshAudit = {
                    auditRefreshTrigger++
                },
                onDismiss = { showSecuritySheet = false }
            )
        }
    }
}

private fun formatDistance(meters: Double): String {
    return if (meters >= 1000.0) {
        "${"%.2f".format(Locale.US, meters / 1000.0)} km"
    } else {
        "${meters.roundToInt()} m"
    }
}
