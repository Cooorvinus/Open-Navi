package com.opennavi.ui.tracks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opennavi.domain.model.RecordedTrack
import com.opennavi.domain.model.RouteResult
import com.opennavi.domain.model.RoutingProfile
import com.opennavi.domain.model.TrackPoint
import com.opennavi.domain.model.Waypoint
import com.opennavi.domain.model.WaypointCategory
import com.opennavi.service.ActiveRecordingSession
import java.util.Locale

/**
 * Panel Nagrywania i Zarządzania Śladami GPX w stylu Locus Map:
 * - Sterowanie usługą pierwszoplanową (Start / Pauza / Zapisz GPX)
 * - Wykres profilu wysokościowego na żywo
 * - Import i eksport plików .gpx
 */
@Composable
fun TracksAndRecordingSheet(
    session: ActiveRecordingSession,
    savedTracks: List<RecordedTrack>,
    onStartRecording: () -> Unit,
    onPauseResumeRecording: () -> Unit,
    onStopAndSaveRecording: (String) -> Unit,
    onImportGpxClick: () -> Unit,
    onExportTrackClick: (RecordedTrack) -> Unit,
    onToggleTrackVisibility: (RecordedTrack) -> Unit,
    onDeleteTrack: (RecordedTrack) -> Unit,
    onFlyToTrack: (RecordedTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    var customTrackName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Nagrywanie i Ślady GPX",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            FilledTonalButton(onClick = onImportGpxClick) {
                Icon(
                    imageVector = Icons.Default.FileOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Importuj GPX")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Karta aktywnego nagrywania w stylu Locus Dashboard
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (session.isRecording) {
                    Color(0xFF1B5E20).copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                }
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                val sec = session.elapsedMillis / 1000L
                val timeFormatted = String.format(
                    Locale.US,
                    "%02d:%02d:%02d",
                    sec / 3600,
                    (sec % 3600) / 60,
                    sec % 60
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatMetricItem("CZAS TRWANIA", timeFormatted)
                    StatMetricItem(
                        "DYSTANS",
                        String.format(Locale.US, "%.2f km", session.totalDistanceMeters / 1000.0)
                    )
                    StatMetricItem(
                        "PRĘDKOŚĆ",
                        String.format(Locale.US, "%.1f km/h", session.currentSpeedKmH)
                    )
                    StatMetricItem(
                        "PRZEWYŻSZENIE",
                        String.format(Locale.US, "+%.0f / -%.0f m", session.elevationGainMeters, session.elevationLossMeters)
                    )
                }

                if (session.points.size >= 2) {
                    Spacer(modifier = Modifier.height(10.dp))
                    ElevationProfileMiniChart(points = session.points)
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!session.isRecording) {
                    Button(
                        onClick = onStartRecording,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rozpocznij nagrywanie śladu GPX (w tle)")
                    }
                } else {
                    OutlinedTextField(
                        value = customTrackName,
                        onValueChange = { customTrackName = it },
                        label = { Text("Nazwa śladu GPX (opcjonalnie)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPauseResumeRecording,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (session.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (session.isPaused) "Wznów" else "Pauza")
                        }

                        Button(
                            onClick = {
                                onStopAndSaveRecording(customTrackName)
                                customTrackName = ""
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Zakończ i zapisz")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Zapisane ślady (${savedTracks.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(savedTracks, key = { it.id }) { track ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "%.2f km  •  +%.0f m / -%.0f m  •  Śr. %.1f km/h",
                                        track.totalDistanceMeters / 1000.0,
                                        track.elevationGainMeters,
                                        track.elevationLossMeters,
                                        track.averageSpeedKmH
                                    ),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row {
                                IconButton(onClick = { onToggleTrackVisibility(track) }) {
                                    Icon(
                                        imageVector = if (track.isVisibleOnMap) {
                                            Icons.Default.Visibility
                                        } else {
                                            Icons.Default.VisibilityOff
                                        },
                                        contentDescription = "Pokaż/Ukryj na mapie"
                                    )
                                }
                                IconButton(onClick = { onFlyToTrack(track) }) {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = "Pokaż na mapie"
                                    )
                                }
                                IconButton(onClick = { onExportTrackClick(track) }) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Eksportuj do pliku GPX"
                                    )
                                }
                                IconButton(onClick = { onDeleteTrack(track) }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Usuń ślad",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        if (track.points.size >= 2) {
                            Spacer(modifier = Modifier.height(6.dp))
                            ElevationProfileMiniChart(points = track.points)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ElevationProfileMiniChart(points: List<TrackPoint>) {
    val altitudes = points.map { it.altitudeMeters.toFloat() }
    val minAlt = altitudes.minOrNull() ?: 0f
    val maxAlt = (altitudes.maxOrNull() ?: 100f).coerceAtLeast(minAlt + 10f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val path = Path()
        val stepX = size.width / (altitudes.size - 1).coerceAtLeast(1)
        altitudes.forEachIndexed { index, alt ->
            val ratio = (alt - minAlt) / (maxAlt - minAlt)
            val x = index * stepX
            val y = size.height - (ratio * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = Color(0xFF43A047),
            style = Stroke(width = 2.5.dp.toPx())
        )
    }
}

/**
 * Arkusz zarządzania punktami Waypoint / POI oraz wyznaczania trasy (Routing OSM).
 */
@Composable
fun WaypointsAndRoutingSheet(
    waypoints: List<Waypoint>,
    activeRoute: RouteResult?,
    isCalculatingRoute: Boolean,
    onAddWaypoint: (name: String, desc: String, category: WaypointCategory, useGps: Boolean) -> Unit,
    onDeleteWaypoint: (Waypoint) -> Unit,
    onFlyToWaypoint: (Waypoint) -> Unit,
    onCalculateRouteToCenter: (RoutingProfile) -> Unit,
    onClearRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(WaypointCategory.PEAK) }
    var useGpsPosition by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Sekcja 1: Wyznaczanie trasy (Routing OSM)
        Text(
            text = "Wyznaczanie Trasy (OSM Routing do celownika)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { onCalculateRouteToCenter(RoutingProfile.HIKING) },
                modifier = Modifier.weight(1f),
                enabled = !isCalculatingRoute
            ) {
                Icon(Icons.Default.Hiking, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Pieszo", fontSize = 12.sp)
            }
            FilledTonalButton(
                onClick = { onCalculateRouteToCenter(RoutingProfile.BICYCLE) },
                modifier = Modifier.weight(1f),
                enabled = !isCalculatingRoute
            ) {
                Icon(Icons.Default.DirectionsBike, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Rower", fontSize = 12.sp)
            }
            FilledTonalButton(
                onClick = { onCalculateRouteToCenter(RoutingProfile.CAR) },
                modifier = Modifier.weight(1f),
                enabled = !isCalculatingRoute
            ) {
                Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Auto", fontSize = 12.sp)
            }
        }

        if (isCalculatingRoute) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Obliczanie optymalnej trasy OpenStreetMap...", fontSize = 12.sp)
            }
        }

        if (activeRoute != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Aktywna trasa: ${activeRoute.profile.labelPl}",
                            fontWeight = FontWeight.Bold
                        )
                        val minutes = (activeRoute.durationSeconds / 60.0).toInt()
                        Text(
                            text = String.format(
                                Locale.US,
                                "Dystans: %.2f km  •  Szacowany czas: %d min",
                                activeRoute.distanceMeters / 1000.0,
                                minutes
                            ),
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(onClick = onClearRoute) {
                        Text("Wyczyść")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sekcja 2: Dodawanie punktów Waypoint / POI
        Text(
            text = "Dodaj Punkt (Waypoint / POI)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nazwa punktu (np. Rysy 2499 m, Schronisko)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WaypointCategory.entries.take(3).forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat.labelPl.substringBefore(" /"), fontSize = 11.sp) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (useGpsPosition) "Lokalizacja: Aktualna pozycja GPS" else "Lokalizacja: Celownik na środku mapy",
                fontSize = 12.sp
            )
            Switch(
                checked = useGpsPosition,
                onCheckedChange = { useGpsPosition = it }
            )
        }

        Button(
            onClick = {
                onAddWaypoint(name, description, selectedCategory, useGpsPosition)
                name = ""
                description = ""
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AddLocationAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Zapisz punkt na mapie")
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(waypoints, key = { it.id }) { wpt ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = wpt.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%s • %.5f, %.5f",
                                    wpt.category.labelPl,
                                    wpt.latitude,
                                    wpt.longitude
                                ),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            IconButton(onClick = { onFlyToWaypoint(wpt) }) {
                                Icon(Icons.Default.MyLocation, contentDescription = "Pokaż na mapie")
                            }
                            IconButton(onClick = { onDeleteWaypoint(wpt) }) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Usuń",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
