package com.anddav.nationaltrailstracker.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.anddav.nationaltrailstracker.data.map.OfflineMapState
import com.anddav.nationaltrailstracker.domain.map.GeoBounds
import com.anddav.nationaltrailstracker.domain.map.GeoPoint
import com.anddav.nationaltrailstracker.domain.map.MapMarker
import com.anddav.nationaltrailstracker.domain.map.StageLocator
import com.anddav.nationaltrailstracker.domain.map.StagePosition
import com.anddav.nationaltrailstracker.ui.parseHexColor
import com.anddav.nationaltrailstracker.ui.stageChipColour
import com.anddav.nationaltrailstracker.ui.theme.TrailTrackerCream
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.util.Locale

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private const val SOURCE_WHOLE = "trail-whole"
private const val SOURCE_STAGE = "trail-stage"
private const val SOURCE_ENDS = "trail-stage-ends"
private const val CAMERA_PADDING_PX = 120
private const val CENTRE_ON_ME_MIN_ZOOM = 15.0
private const val POSITION_POLL_MILLIS = 3_000L
private const val SHOW_ACCURACY_ABOVE_METRES = 100f

@Composable
fun TrailMapScreen(viewModel: TrailMapViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    val trail = uiState.trail
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        viewModel.onPermissionResult(grants.values.any { it })
    }
    // Bumped each time "Centre on me" is tapped.
    var centreRequests by remember { mutableIntStateOf(0) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    val trailColour = trail?.let { parseHexColor(it.colour) } ?: MaterialTheme.colorScheme.primary
    val stageIndex = uiState.stageIndex
    val stageDef = stageIndex?.let { trail?.defaultStages?.getOrNull(it) }
    val stageColour = if (trail != null && stageIndex != null) {
        stageChipColour(trail.colour, stageIndex, trail.defaultStages.size)
    } else {
        trailColour
    }
    val title = if (stageIndex != null && stageDef != null) "Stage ${stageIndex + 1}" else (trail?.name ?: "Map")

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(trailColour)
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        ) {
            Text(
                text = title,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = TrailTrackerCream,
                modifier = Modifier.semantics { heading() },
            )
            if (stageDef != null) {
                Text("${stageDef.fromLandmark} → ${stageDef.toLandmark}", color = TrailTrackerCream)
            }
            OfflineButton(uiState.offline, onSave = viewModel::saveOffline)
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (uiState.wholeLine.isNotEmpty()) {
                TrailMap(
                    uiState = uiState,
                    contentDescription = buildString {
                        append("Map of ")
                        append(if (stageDef != null) "$title, ${stageDef.fromLandmark} to ${stageDef.toLandmark}" else title)
                    },
                    wholeColour = trailColour.toArgb(),
                    stageColour = stageColour.toArgb(),
                    centreRequests = centreRequests,
                    onLocation = viewModel::onLocation,
                    onBaseMapOffline = viewModel::onBaseMapOffline,
                    modifier = Modifier.fillMaxSize(),
                )
                if (uiState.showLocation && stageIndex != null) {
                    PositionPanel(uiState, modifier = Modifier.align(Alignment.TopCenter).padding(12.dp))
                }
                ExtendedFloatingActionButton(
                    onClick = {
                        when {
                            uiState.showLocation -> centreRequests++
                            viewModel.hasLocationPermission() -> viewModel.onShowLocation()
                            else -> permissionLauncher.launch(LOCATION_PERMISSIONS)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    Text(if (uiState.showLocation) "Centre on me" else "Show my location")
                }
            } else if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp))
        }
    }
}

@Composable
private fun OfflineButton(state: OfflineMapState, onSave: () -> Unit) {
    val (label, enabled) = when (state) {
        OfflineMapState.Unknown -> return
        OfflineMapState.NotSaved -> "Save map for offline use" to true
        is OfflineMapState.Downloading -> (state.percent?.let { "Saving map… $it%" } ?: "Saving map…") to false
        OfflineMapState.Saved -> "Map saved for offline use ✓" to false
        is OfflineMapState.Failed -> "Saving failed: try again" to true
    }
    TextButton(onClick = onSave, enabled = enabled) {
        Text(label, color = TrailTrackerCream.copy(alpha = if (enabled) 1f else 0.85f))
    }
}

/** "4.2 of 13.1 mi along · 8.9 mi to Staines", measured along the map line. */
@Composable
private fun PositionPanel(uiState: TrailMapUiState, modifier: Modifier = Modifier) {
    val position = uiState.position
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            if (position == null) {
                Text("Finding your position…", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            val main = when (position) {
                is StagePosition.InStage ->
                    "${miles(position.milesAlong)} of ${miles(position.stageMiles)} mi along · " +
                        "${miles(position.milesToEnd)} mi to ${position.endName}"
                is StagePosition.BeforeStart -> "${miles(position.milesToStart)} mi before the start, at ${position.startName}"
                is StagePosition.PastEnd -> "${miles(position.milesPastEnd)} mi past the end, at ${position.endName}"
            }
            Text(main, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (position.offRouteMetres > StageLocator.OFF_ROUTE_WARNING_METRES) {
                Text(
                    "⚠ ${distance(position.offRouteMetres)} from the route",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            uiState.accuracyMetres?.takeIf { it > SHOW_ACCURACY_ABOVE_METRES }?.let {
                Text(
                    "Location accurate to about ${distance(it.toDouble())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Distances along the map line",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun miles(value: Double) = "%.1f".format(Locale.UK, value)

private fun distance(metres: Double) =
    if (metres < 1000) "%.0f m".format(Locale.UK, metres) else "%.1f mi".format(Locale.UK, metres / 1609.344)

@Composable
private fun TrailMap(
    uiState: TrailMapUiState,
    contentDescription: String,
    wholeColour: Int,
    stageColour: Int,
    centreRequests: Int,
    onLocation: (lat: Double, lon: Double, accuracyMetres: Float?) -> Unit,
    onBaseMapOffline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnLocation by rememberUpdatedState(onLocation)
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    // Fit the camera once, not on every state change.
    var cameraFitted by rememberSaveable { mutableStateOf(false) }

    // Forward the screen's lifecycle to the MapView. Stopping the MapView also stops location updates.
    DisposableEffect(lifecycleOwner, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.isAttributionEnabled = true
            m.uiSettings.isLogoEnabled = true
            var fellBack = false
            mapView.addOnDidFailLoadingMapListener {
                // No connection and nothing saved: show the trail on a plain background instead.
                if (!fellBack) {
                    fellBack = true
                    onBaseMapOffline()
                    m.setStyle(Style.Builder().fromJson(MapConfig.OFFLINE_STYLE_JSON)) { s ->
                        addTrailLayers(s, wholeColour, stageColour)
                        style = s
                    }
                }
            }
            m.setStyle(Style.Builder().fromUri(MapConfig.STYLE_URL)) { s ->
                addTrailLayers(s, wholeColour, stageColour)
                style = s
            }
            map = m
        }
    }

    LaunchedEffect(style, uiState.wholeLine, uiState.stageLine, uiState.stageEnds) {
        val s = style ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(SOURCE_WHOLE)?.setGeoJson(lineFeatures(uiState.wholeLine))
        s.getSourceAs<GeoJsonSource>(SOURCE_STAGE)?.setGeoJson(lineFeatures(uiState.stageLine))
        s.getSourceAs<GeoJsonSource>(SOURCE_ENDS)?.setGeoJson(endFeatures(uiState.stageEnds))
    }

    LaunchedEffect(map, uiState.initialBounds) {
        val m = map ?: return@LaunchedEffect
        val bounds = uiState.initialBounds ?: return@LaunchedEffect
        if (!cameraFitted) {
            mapView.post { m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.toLatLngBounds(), CAMERA_PADDING_PX)) }
            cameraFitted = true
        }
    }

    // Live location: only once the walker has asked for it, and only while the screen is started.
    LaunchedEffect(map, style, uiState.showLocation) {
        val m = map ?: return@LaunchedEffect
        val s = style ?: return@LaunchedEffect
        if (!uiState.showLocation || !enableLocation(context, m, s)) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                m.locationComponent.lastKnownLocation?.let { loc ->
                    currentOnLocation(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else null)
                }
                delay(POSITION_POLL_MILLIS)
            }
        }
    }

    LaunchedEffect(centreRequests) {
        if (centreRequests == 0) return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        val here = m.locationComponent.takeIf { it.isLocationComponentActivated }?.lastKnownLocation ?: return@LaunchedEffect
        val zoom = maxOf(m.cameraPosition.zoom, CENTRE_ON_ME_MIN_ZOOM)
        m.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(LatLng(here.latitude, here.longitude)).zoom(zoom).build(),
            ),
        )
    }

    AndroidView(
        factory = { mapView },
        update = { it.contentDescription = contentDescription },
        modifier = modifier,
    )
}

private fun addTrailLayers(style: Style, wholeColour: Int, stageColour: Int) {
    style.addSource(GeoJsonSource(SOURCE_WHOLE))
    style.addSource(GeoJsonSource(SOURCE_STAGE))
    style.addSource(GeoJsonSource(SOURCE_ENDS))
    style.addLayer(
        LineLayer("trail-whole-line", SOURCE_WHOLE).withProperties(
            PropertyFactory.lineColor(wholeColour),
            PropertyFactory.lineWidth(3f),
            PropertyFactory.lineOpacity(0.75f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        LineLayer("trail-stage-casing", SOURCE_STAGE).withProperties(
            PropertyFactory.lineColor(android.graphics.Color.WHITE),
            PropertyFactory.lineWidth(8f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        LineLayer("trail-stage-line", SOURCE_STAGE).withProperties(
            PropertyFactory.lineColor(stageColour),
            PropertyFactory.lineWidth(5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        CircleLayer("trail-stage-ends", SOURCE_ENDS).withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor(stageColour),
            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
            PropertyFactory.circleStrokeWidth(2.5f),
        ),
    )
    style.addLayer(
        SymbolLayer("trail-stage-end-labels", SOURCE_ENDS).withProperties(
            PropertyFactory.textField(Expression.get("name")),
            PropertyFactory.textFont(MapConfig.LABEL_FONT),
            PropertyFactory.textSize(13f),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
            PropertyFactory.textOffset(arrayOf(0f, 1.1f)),
            PropertyFactory.textColor(android.graphics.Color.BLACK),
            PropertyFactory.textHaloColor(android.graphics.Color.WHITE),
            PropertyFactory.textHaloWidth(1.5f),
        ),
    )
}

/**
 * Turns on the live location dot. Only called after the walker asked for it. Returns false (and
 * does nothing) if permission isn't held.
 */
private fun enableLocation(context: Context, map: MapLibreMap, style: Style): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return false
    val component = map.locationComponent
    if (!component.isLocationComponentActivated) {
        component.activateLocationComponent(
            LocationComponentActivationOptions.builder(context, style)
                .useDefaultLocationEngine(true)
                .build(),
        )
    }
    component.isLocationComponentEnabled = true
    component.cameraMode = CameraMode.NONE
    component.renderMode = RenderMode.COMPASS
    return true
}

private fun lineFeatures(points: List<GeoPoint>): FeatureCollection =
    if (points.size < 2) {
        FeatureCollection.fromFeatures(emptyList())
    } else {
        FeatureCollection.fromFeature(
            Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.lon, it.lat) })),
        )
    }

private fun endFeatures(ends: Pair<MapMarker, MapMarker>?): FeatureCollection =
    FeatureCollection.fromFeatures(
        ends?.toList().orEmpty().map { marker ->
            Feature.fromGeometry(Point.fromLngLat(marker.point.lon, marker.point.lat)).apply {
                addStringProperty("name", marker.name)
            }
        },
    )

private fun GeoBounds.toLatLngBounds(): LatLngBounds = LatLngBounds.from(north, east, south, west)
