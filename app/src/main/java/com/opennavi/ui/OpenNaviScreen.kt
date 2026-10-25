package com.opennavi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.opennavi.domain.model.MapOrientationMode
import com.opennavi.domain.model.MapStyleTheme
import com.opennavi.domain.model.RecordedTrack
import com.opennavi.ui.map.LocusCompassOverlay
import com.opennavi.ui.map.LocusMapCrosshair
import com.opennavi.ui.map.LocusTopTelemetryBar
import com.opennavi.ui.map.OpenNaviMapLibreView
import com.opennavi.ui.offlinemaps.OfflineMapsManagerSheet
import com.opennavi.ui.tracks.TracksAndRecordingSheet
import com.opennavi.ui.tracks.WaypointsAndRoutingSheet
import java.util.Locale

private enum class ActiveBottomPanel {
    NONE,
    OFFLINE_MAPS,
    TRACKS_GPX,
    WAYPOINTS_ROUTING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenNaviScreen(
    viewModel: OpenNaviViewModel,
    onRequestImportGpxFile: () -> Unit,
    onRequestExportGpxFile: (RecordedTrack) -> Unit
) {
    val gpsStatus by viewModel.gpsStatus.collectAsState()
    val cameraState by viewModel.cameraState.collectAsState()
    val recordingSession by viewModel.recordingSession.collectAsState()
    val countries by viewModel.countriesOfflineMaps.collectAsState()
    val savedTracks by viewModel.savedTracks.collectAsState()
    val savedWaypoints by viewModel.savedWaypoints.collectAsState()
    val activeRoute by viewModel.activeRoute.collectAsState()
    val isCalculatingRoute by viewModel.isCalculatingRoute.collectAsState()
    val bannerMessage by viewModel.statusBannerMessage.collectAsState()

    var activeBottomPanel by remember { mutableStateOf(ActiveBottomPanel.NONE) }
    var showStyleDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Główna mapa wektorowa MapLibre + OpenStreetMap
        OpenNaviMapLibreView(
            modifier = Modifier.fillMaxSize(),
            cameraState = cameraState,
            gpsStatus = gpsStatus,
            activeTrackPoints = recordingSession.points,
            savedTracks = savedTracks,
            waypoints = savedWaypoints,
            activeRoute = activeRoute,
            onMapMovedByUser = { lat, lon, zoom ->
                viewModel.updateMapCenterFromGesture(lat, lon, zoom)
            }
        )

        // 2. Centralny celownik (Crosshair) w stylu Locus Map
        LocusMapCrosshair(
            visible = !cameraState.followGps,
            modifier = Modifier.align(Alignment.Center)
        )

        // 3. Górny pasek telemetryczny GPS / GNSS / Wysokościomierz (zawsze na wierzchu po rozwinięciu)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .zIndex(10f)
        ) {
            LocusTopTelemetryBar(
                gpsStatus = gpsStatus,
                cameraState = cameraState
            )

            // Pasek statusu aktywnego nagrywania GPX (jeśli trwa)
            if (recordingSession.isRecording) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFD32F2F).copy(alpha = 0.92f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (recordingSession.isPaused) "PAUZA GPX" else "NAGRYWANIE GPX",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = String.format(
                                Locale.US,
                                "%.2f km  |  +%.0f m",
                                recordingSession.totalDistanceMeters / 1000.0,
                                recordingSession.elevationGainMeters
                            ),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 4. Lewa kolumna: Róża kompasu + przełącznik widoku 3D (Tilt)
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LocusCompassOverlay(
                azimuthDegrees = gpsStatus.compassAzimuthDegrees,
                orientationMode = cameraState.orientationMode,
                onClickResetNorth = { viewModel.centerOnGpsAndCycleOrientation() }
            )

            SmallFloatingActionButton(
                onClick = { viewModel.toggle3DTilt() },
                containerColor = Color(0xE6161B22),
                contentColor = if (cameraState.tiltDegrees > 10.0) Color(0xFF4CAF50) else Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Terrain,
                    contentDescription = "Nachylenie 3D"
                )
            }
        }

        // 5. Prawa kolumna: Przyciski Zoom (+/-) oraz przycisk centrowania GPS / Kompas
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { viewModel.zoomBy(1.0) },
                containerColor = Color(0xE6161B22),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Przybliż")
            }

            SmallFloatingActionButton(
                onClick = { viewModel.zoomBy(-1.0) },
                containerColor = Color(0xE6161B22),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Oddal")
            }

            Spacer(modifier = Modifier.height(6.dp))

            FloatingActionButton(
                onClick = { viewModel.centerOnGpsAndCycleOrientation() },
                containerColor = if (cameraState.followGps) Color(0xFF1B5E20) else Color(0xE6161B22),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                val icon = when (cameraState.orientationMode) {
                    MapOrientationMode.NORTH_UP -> Icons.Default.GpsFixed
                    MapOrientationMode.COMPASS_ROTATE -> Icons.Default.Explore
                    MapOrientationMode.GPS_BEARING -> Icons.Default.Route
                }
                Icon(icon, contentDescription = "Centruj na GPS / Zmień tryb obrotu")
            }
        }

        // 6. Automatycznie znikające powiadomienie nad dolnym paskiem (bez konieczności klikania OK)
        if (bannerMessage != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 112.dp, start = 16.dp, end = 16.dp)
                    .zIndex(8f),
                containerColor = Color(0xF0161B22),
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = bannerMessage.orEmpty(),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 7. Dolny pasek narzędziowy w stylu Locus Map (wysoki kontrast białych ikon i tekstów)
        val navItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color(0xFF1B5E20),
            selectedTextColor = Color.White,
            indicatorColor = Color(0xFFA5D6A7),
            unselectedIconColor = Color.White,
            unselectedTextColor = Color(0xFFECEFF1)
        )

        NavigationBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            containerColor = Color(0xF5161B22),
            contentColor = Color.White
        ) {
            NavigationBarItem(
                selected = activeBottomPanel == ActiveBottomPanel.OFFLINE_MAPS,
                onClick = { activeBottomPanel = ActiveBottomPanel.OFFLINE_MAPS },
                colors = navItemColors,
                icon = {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Mapy Państw Offline"
                    )
                },
                label = {
                    Text(
                        text = "Mapy Państw",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            )

            NavigationBarItem(
                selected = activeBottomPanel == ActiveBottomPanel.TRACKS_GPX,
                onClick = { activeBottomPanel = ActiveBottomPanel.TRACKS_GPX },
                colors = navItemColors,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = "Nagrywanie i Ślady GPX",
                        tint = if (recordingSession.isRecording) Color(0xFFFF5252) else Color.White
                    )
                },
                label = {
                    Text(
                        text = if (recordingSession.isRecording) "REC GPX" else "Ślady GPX",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            )

            NavigationBarItem(
                selected = activeBottomPanel == ActiveBottomPanel.WAYPOINTS_ROUTING,
                onClick = { activeBottomPanel = ActiveBottomPanel.WAYPOINTS_ROUTING },
                colors = navItemColors,
                icon = {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Punkty POI i Trasa"
                    )
                },
                label = {
                    Text(
                        text = "POI i Trasa",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            )

            NavigationBarItem(
                selected = showStyleDialog,
                onClick = { showStyleDialog = true },
                colors = navItemColors,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Style OSM"
                    )
                },
                label = {
                    Text(
                        text = "Warstwy OSM",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            )
        }
    }

    // Dolne arkusze (ModalBottomSheet) dla poszczególnych modułów
    if (activeBottomPanel != ActiveBottomPanel.NONE) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { activeBottomPanel = ActiveBottomPanel.NONE },
            sheetState = sheetState,
            contentWindowInsets = { WindowInsets(0) }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 580.dp)
                    .navigationBarsPadding()
            ) {
                when (activeBottomPanel) {
                    ActiveBottomPanel.OFFLINE_MAPS -> {
                        OfflineMapsManagerSheet(
                            countries = countries,
                            onDownloadOrUpdateCountry = { code ->
                                viewModel.downloadOrUpdateCountryMap(code)
                            },
                            onCheckAllUpdates = {
                                viewModel.checkCountryMapUpdates()
                            },
                            onDeleteCountryMap = { code ->
                                viewModel.deleteCountryOfflineMap(code)
                            },
                            onFlyToCountry = { country ->
                                viewModel.flyToLocation(
                                    lat = country.boundingBox.centerLat,
                                    lon = country.boundingBox.centerLon,
                                    zoom = 6.2
                                )
                                activeBottomPanel = ActiveBottomPanel.NONE
                            }
                        )
                    }

                    ActiveBottomPanel.TRACKS_GPX -> {
                        TracksAndRecordingSheet(
                            session = recordingSession,
                            savedTracks = savedTracks,
                            onStartRecording = { viewModel.startTrackRecording() },
                            onPauseResumeRecording = { viewModel.pauseOrResumeTrackRecording() },
                            onStopAndSaveRecording = { name ->
                                viewModel.stopAndSaveTrackRecording(name)
                            },
                            onImportGpxClick = onRequestImportGpxFile,
                            onExportTrackClick = onRequestExportGpxFile,
                            onToggleTrackVisibility = { viewModel.toggleSavedTrackVisibility(it) },
                            onDeleteTrack = { viewModel.deleteSavedTrack(it) },
                            onFlyToTrack = { trk ->
                                trk.points.firstOrNull()?.let { pt ->
                                    viewModel.flyToLocation(pt.latitude, pt.longitude, 13.5)
                                    activeBottomPanel = ActiveBottomPanel.NONE
                                }
                            }
                        )
                    }

                    ActiveBottomPanel.WAYPOINTS_ROUTING -> {
                        WaypointsAndRoutingSheet(
                            waypoints = savedWaypoints,
                            activeRoute = activeRoute,
                            isCalculatingRoute = isCalculatingRoute,
                            onAddWaypoint = { name, desc, cat, useGps ->
                                viewModel.addWaypointAtMapCenter(name, desc, cat, useGps)
                            },
                            onDeleteWaypoint = { viewModel.deleteWaypoint(it) },
                            onFlyToWaypoint = { wpt ->
                                viewModel.flyToLocation(wpt.latitude, wpt.longitude, 15.0)
                                activeBottomPanel = ActiveBottomPanel.NONE
                            },
                            onCalculateRouteToCenter = { profile ->
                                viewModel.calculateRouteFromGpsToMapCenter(profile)
                            },
                            onClearRoute = { viewModel.clearActiveRoute() }
                        )
                    }

                    ActiveBottomPanel.NONE -> Unit
                }
            }
        }
    }

    // Dialog wyboru darmowego stylu wektorowego OpenStreetMap
    if (showStyleDialog) {
        AlertDialog(
            onDismissRequest = { showStyleDialog = false },
            title = { Text("Styl Mapy Wektorowej (OpenStreetMap)") },
            text = {
                Column {
                    MapStyleTheme.entries.forEach { theme ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = cameraState.styleTheme == theme,
                                onClick = {
                                    viewModel.selectMapStyle(theme)
                                    showStyleDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = theme.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = theme.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStyleDialog = false }) {
                    Text("Zamknij")
                }
            }
        )
    }
}
