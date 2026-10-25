package com.opennavi.ui.map

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.opennavi.domain.model.GpsStatus
import com.opennavi.domain.model.MapOrientationMode
import com.opennavi.domain.model.RecordedTrack
import com.opennavi.domain.model.RouteResult
import com.opennavi.domain.model.TrackPoint
import com.opennavi.domain.model.Waypoint
import com.opennavi.ui.MapCameraState
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Natywny komponent MapLibre w Jetpack Compose obsługujący:
 * - Wektorowe i topograficzne mapy OpenStreetMap
 * - Warstwy GeoJSON dla aktualnie nagrywanego śladu GPX, zapisanych śladów, wyznaczonej trasy i punktów POI
 * - Płynne szczypanie mapy (pinch-to-zoom) i przesuwanie bez nadpisywania poziomu powiększenia
 */
@Composable
fun OpenNaviMapLibreView(
    modifier: Modifier = Modifier,
    cameraState: MapCameraState,
    gpsStatus: GpsStatus,
    activeTrackPoints: List<TrackPoint>,
    savedTracks: List<RecordedTrack>,
    waypoints: List<Waypoint>,
    activeRoute: RouteResult?,
    onMapMovedByUser: (lat: Double, lon: Double, zoom: Double) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
        }
    }

    var mapLibreInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentLoadedStyleUrl by remember { mutableStateOf("") }
    var isUserGestureInProgress by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Aktualizacja stylu mapy i warstw GeoJSON (ślady GPX, punkty POI, pozycja GPS, routing)
    LaunchedEffect(
        mapLibreInstance,
        cameraState.styleTheme,
        gpsStatus,
        activeTrackPoints,
        savedTracks,
        waypoints,
        activeRoute
    ) {
        val map = mapLibreInstance ?: return@LaunchedEffect
        val desiredStyleUrl = cameraState.styleTheme.styleUrl

        if (currentLoadedStyleUrl != desiredStyleUrl) {
            currentLoadedStyleUrl = desiredStyleUrl
            val styleBuilder = if (cameraState.styleTheme.isInlineJson) {
                Style.Builder().fromJson(desiredStyleUrl)
            } else {
                Style.Builder().fromUri(desiredStyleUrl)
            }
            map.setStyle(styleBuilder) { style ->
                setupOrUpdateMapOverlays(
                    style = style,
                    gpsStatus = gpsStatus,
                    activeTrackPoints = activeTrackPoints,
                    savedTracks = savedTracks,
                    waypoints = waypoints,
                    activeRoute = activeRoute
                )
            }
        } else {
            map.getStyle { style ->
                setupOrUpdateMapOverlays(
                    style = style,
                    gpsStatus = gpsStatus,
                    activeTrackPoints = activeTrackPoints,
                    savedTracks = savedTracks,
                    waypoints = waypoints,
                    activeRoute = activeRoute
                )
            }
        }
    }

    // 1. Jawne komendy kamery (przyciski +/-, przełącznik 3D, "Pokaż na mapie", przycisk centrowania GPS)
    LaunchedEffect(mapLibreInstance, cameraState.cameraCommandId) {
        val map = mapLibreInstance ?: return@LaunchedEffect
        val targetLat = if (cameraState.followGps) gpsStatus.latitude else cameraState.centerLat
        val targetLon = if (cameraState.followGps) gpsStatus.longitude else cameraState.centerLon
        val targetBearing = when (cameraState.orientationMode) {
            MapOrientationMode.NORTH_UP -> 0.0
            MapOrientationMode.COMPASS_ROTATE -> gpsStatus.compassAzimuthDegrees.toDouble()
            MapOrientationMode.GPS_BEARING -> gpsStatus.bearingDegrees.toDouble()
        }

        val cameraPosition = CameraPosition.Builder()
            .target(LatLng(targetLat, targetLon))
            .zoom(cameraState.zoom)
            .tilt(cameraState.tiltDegrees)
            .bearing(targetBearing)
            .build()

        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 300)
    }

    // 2. Płynne podążanie za GPS i kompasem (tylko gdy tryb followGps jest włączony i użytkownik nie wykonuje gestu)
    LaunchedEffect(
        mapLibreInstance,
        cameraState.followGps,
        cameraState.orientationMode,
        gpsStatus.latitude,
        gpsStatus.longitude,
        gpsStatus.compassAzimuthDegrees,
        gpsStatus.bearingDegrees
    ) {
        val map = mapLibreInstance ?: return@LaunchedEffect
        if (!cameraState.followGps || isUserGestureInProgress) return@LaunchedEffect

        val targetBearing = when (cameraState.orientationMode) {
            MapOrientationMode.NORTH_UP -> 0.0
            MapOrientationMode.COMPASS_ROTATE -> gpsStatus.compassAzimuthDegrees.toDouble()
            MapOrientationMode.GPS_BEARING -> gpsStatus.bearingDegrees.toDouble()
        }

        // Zachowujemy aktualny zoom z MapLibre podczas podążania za pozycją GPS
        val currentMapZoom = map.cameraPosition.zoom.takeIf { it > 1.0 } ?: cameraState.zoom

        val cameraPosition = CameraPosition.Builder()
            .target(LatLng(gpsStatus.latitude, gpsStatus.longitude))
            .zoom(currentMapZoom)
            .tilt(cameraState.tiltDegrees)
            .bearing(targetBearing)
            .build()

        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 300)
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.apply {
                getMapAsync { map ->
                    map.uiSettings.apply {
                        isCompassEnabled = false // Posiadamy własny kompas w stylu Locus Map
                        isLogoEnabled = false
                        isAttributionEnabled = true
                        isRotateGesturesEnabled = true
                        isTiltGesturesEnabled = true
                        isZoomGesturesEnabled = true
                        isScrollGesturesEnabled = true
                    }

                    map.addOnCameraMoveStartedListener { reason ->
                        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                            isUserGestureInProgress = true
                        }
                    }

                    map.addOnCameraMoveListener {
                        if (isUserGestureInProgress) {
                            val pos = map.cameraPosition
                            pos.target?.let { target ->
                                onMapMovedByUser(target.latitude, target.longitude, pos.zoom)
                            }
                        }
                    }

                    map.addOnCameraIdleListener {
                        val pos = map.cameraPosition
                        if (isUserGestureInProgress) {
                            isUserGestureInProgress = false
                        }
                        pos.target?.let { target ->
                            onMapMovedByUser(target.latitude, target.longitude, pos.zoom)
                        }
                    }

                    mapLibreInstance = map
                }
            }
        }
    )
}

private const val SOURCE_GPS_MARKER = "source_gps_marker"
private const val LAYER_GPS_ACCURACY = "layer_gps_accuracy"
private const val LAYER_GPS_MARKER = "layer_gps_marker"

private const val SOURCE_ACTIVE_TRACK = "source_active_track"
private const val LAYER_ACTIVE_TRACK = "layer_active_track"

private const val SOURCE_SAVED_TRACKS = "source_saved_tracks"
private const val LAYER_SAVED_TRACKS = "layer_saved_tracks"

private const val SOURCE_ROUTE = "source_route"
private const val LAYER_ROUTE = "layer_route"

private const val SOURCE_WAYPOINTS = "source_waypoints"
private const val LAYER_WAYPOINTS = "layer_waypoints"

private fun setupOrUpdateMapOverlays(
    style: Style,
    gpsStatus: GpsStatus,
    activeTrackPoints: List<TrackPoint>,
    savedTracks: List<RecordedTrack>,
    waypoints: List<Waypoint>,
    activeRoute: RouteResult?
) {
    // 1. Zapisane ślady GPX
    val visibleTracks = savedTracks.filter { it.isVisibleOnMap && it.points.size >= 2 }
    val savedTracksGeoJson = buildMultiLineGeoJson(
        visibleTracks.map { trk -> trk.points.map { it.latitude to it.longitude } }
    )
    updateGeoJsonSource(style, SOURCE_SAVED_TRACKS, savedTracksGeoJson)
    if (style.getLayer(LAYER_SAVED_TRACKS) == null) {
        style.addLayer(
            LineLayer(LAYER_SAVED_TRACKS, SOURCE_SAVED_TRACKS).withProperties(
                PropertyFactory.lineColor(Color.parseColor("#D81B60")),
                PropertyFactory.lineWidth(4.5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }

    // 2. Wyznaczona trasa (Routing)
    val routeGeoJson = if (activeRoute != null && activeRoute.geometryPoints.size >= 2) {
        buildMultiLineGeoJson(listOf(activeRoute.geometryPoints))
    } else {
        emptyFeatureCollection()
    }
    updateGeoJsonSource(style, SOURCE_ROUTE, routeGeoJson)
    if (style.getLayer(LAYER_ROUTE) == null) {
        style.addLayer(
            LineLayer(LAYER_ROUTE, SOURCE_ROUTE).withProperties(
                PropertyFactory.lineColor(Color.parseColor("#1E88E5")),
                PropertyFactory.lineWidth(6.0f),
                PropertyFactory.lineOpacity(0.88f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }

    // 3. Aktualnie nagrywany ślad GPX na żywo
    val activeTrackGeoJson = if (activeTrackPoints.size >= 2) {
        buildMultiLineGeoJson(listOf(activeTrackPoints.map { it.latitude to it.longitude }))
    } else {
        emptyFeatureCollection()
    }
    updateGeoJsonSource(style, SOURCE_ACTIVE_TRACK, activeTrackGeoJson)
    if (style.getLayer(LAYER_ACTIVE_TRACK) == null) {
        style.addLayer(
            LineLayer(LAYER_ACTIVE_TRACK, SOURCE_ACTIVE_TRACK).withProperties(
                PropertyFactory.lineColor(Color.parseColor("#FF3D00")),
                PropertyFactory.lineWidth(5.5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }

    // 4. Punkty użytkownika (Waypoints / POI)
    val waypointsGeoJson = buildPointsGeoJson(
        waypoints.map { it.latitude to it.longitude }
    )
    updateGeoJsonSource(style, SOURCE_WAYPOINTS, waypointsGeoJson)
    if (style.getLayer(LAYER_WAYPOINTS) == null) {
        style.addLayer(
            CircleLayer(LAYER_WAYPOINTS, SOURCE_WAYPOINTS).withProperties(
                PropertyFactory.circleRadius(8.5f),
                PropertyFactory.circleColor(Color.parseColor("#FFA000")),
                PropertyFactory.circleStrokeWidth(2.5f),
                PropertyFactory.circleStrokeColor(Color.WHITE)
            )
        )
    }

    // 5. Pozycja GPS użytkownika
    val gpsGeoJson = buildPointsGeoJson(listOf(gpsStatus.latitude to gpsStatus.longitude))
    updateGeoJsonSource(style, SOURCE_GPS_MARKER, gpsGeoJson)
    if (style.getLayer(LAYER_GPS_ACCURACY) == null) {
        style.addLayer(
            CircleLayer(LAYER_GPS_ACCURACY, SOURCE_GPS_MARKER).withProperties(
                PropertyFactory.circleRadius(18f),
                PropertyFactory.circleColor(Color.parseColor("#332979FF")),
                PropertyFactory.circleStrokeWidth(1.5f),
                PropertyFactory.circleStrokeColor(Color.parseColor("#882979FF"))
            )
        )
    }
    if (style.getLayer(LAYER_GPS_MARKER) == null) {
        style.addLayer(
            CircleLayer(LAYER_GPS_MARKER, SOURCE_GPS_MARKER).withProperties(
                PropertyFactory.circleRadius(7.5f),
                PropertyFactory.circleColor(Color.parseColor("#2962FF")),
                PropertyFactory.circleStrokeWidth(3.0f),
                PropertyFactory.circleStrokeColor(Color.WHITE)
            )
        )
    }
}

private fun updateGeoJsonSource(style: Style, sourceId: String, geoJson: String) {
    val existing = style.getSourceAs<GeoJsonSource>(sourceId)
    if (existing != null) {
        existing.setGeoJson(geoJson)
    } else {
        style.addSource(GeoJsonSource(sourceId, geoJson))
    }
}

private fun emptyFeatureCollection(): String =
    """{"type":"FeatureCollection","features":[]}"""

private fun buildPointsGeoJson(points: List<Pair<Double, Double>>): String {
    if (points.isEmpty()) return emptyFeatureCollection()
    val features = points.joinToString(",") { (lat, lon) ->
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

private fun buildMultiLineGeoJson(lines: List<List<Pair<Double, Double>>>): String {
    if (lines.isEmpty()) return emptyFeatureCollection()
    val features = lines.filter { it.size >= 2 }.joinToString(",") { pts ->
        val coords = pts.joinToString(",") { (lat, lon) -> "[$lon,$lat]" }
        """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]},"properties":{}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}
