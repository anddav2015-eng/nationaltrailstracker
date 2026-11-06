package com.anddav.nationaltrailstracker.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.anddav.nationaltrailstracker.domain.map.GeoBounds
import com.anddav.nationaltrailstracker.domain.map.GeoPoint
import com.anddav.nationaltrailstracker.domain.map.MapLineVersion
import com.anddav.nationaltrailstracker.domain.map.MapMarker
import com.anddav.nationaltrailstracker.ui.parseHexColor
import com.anddav.nationaltrailstracker.ui.stageChipColour
import com.anddav.nationaltrailstracker.ui.theme.TrailTrackerCream
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

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private const val SOURCE_WHOLE = "trail-whole"
private const val SOURCE_STAGE = "trail-stage"
private const val SOURCE_ENDS = "trail-stage-ends"
private const val CAMERA_PADDING_PX = 120
private const val CENTRE_ON_ME_MIN_ZOOM = 15.0

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

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(trailColour)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = if (stageIndex != null && stageDef != null) "Stage ${stageIndex + 1}" else (trail?.name ?: "Map"),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = TrailTrackerCream,
                modifier = Modifier.semantics { heading() },
            )
            if (stageDef != null) {
                Text("${stageDef.fromLandmark} → ${stageDef.toLandmark}", color = TrailTrackerCream)
            }
            if (uiState.versions.size > 1) {
                VersionPicker(uiState, onSelect = viewModel::selectVersion)
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (uiState.version != null) {
                TrailMap(
                    uiState = uiState,
                    wholeColour = trailColour.toArgb(),
                    stageColour = stageColour.toArgb(),
                    centreRequests = centreRequests,
                    onBaseMapOffline = viewModel::onBaseMapOffline,
                    modifier = Modifier.fillMaxSize(),
                )
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            val routeCredit = if (uiState.version?.fromOpenStreetMap == true) {
                MapConfig.OSM_ROUTE_CREDIT
            } else {
                MapConfig.OFFICIAL_ROUTE_CREDIT
            }
            Text(routeCredit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(MapConfig.BASE_MAP_CREDIT, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Temporary: lets the owner compare the bundled route lines at full zoom. */
@Composable
private fun VersionPicker(uiState: TrailMapUiState, onSelect: (MapLineVersion) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { expanded = true }) {
            Text("Line: ${uiState.version?.label ?: "…"} ▾", color = TrailTrackerCream)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            uiState.versions.forEach { version ->
                DropdownMenuItem(
                    text = { Text(version.label) },
                    onClick = {
                        expanded = false
                        onSelect(version)
                    },
                )
            }
        }
    }
}

@Composable
private fun TrailMap(
    uiState: TrailMapUiState,
    wholeColour: Int,
    stageColour: Int,
    centreRequests: Int,
    onBaseMapOffline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    // Fit the camera once; switching line versions must not move it, so versions can be compared.
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
                // No connection (and nothing cached): show the trail on a plain background instead.
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

    LaunchedEffect(map, style, uiState.showLocation) {
        val m = map ?: return@LaunchedEffect
        val s = style ?: return@LaunchedEffect
        if (uiState.showLocation) enableLocation(context, m, s)
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

    AndroidView(factory = { mapView }, modifier = modifier)
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

/** Turns on the live location dot. Only called after the walker asked for it and permission is held. */
private fun enableLocation(context: Context, map: MapLibreMap, style: Style) {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return
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
