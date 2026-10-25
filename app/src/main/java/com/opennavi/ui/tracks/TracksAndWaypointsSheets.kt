package com.opennavi.ui.tracks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opennavi.data.gpx.GpxSerializer
import com.opennavi.domain.model.GpsStatus
import com.opennavi.domain.model.RecordedTrack
import com.opennavi.domain.model.RouteResult
import com.opennavi.domain.model.RoutingProfile
import com.opennavi.domain.model.TrackPoint
import com.opennavi.domain.model.Waypoint
import com.opennavi.domain.model.WaypointCategory
import com.opennavi.service.ActiveRecordingSession
import com.opennavi.ui.MapCameraState
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

private enum class CoordinateSourceMode(val label: String) {
    MAP_CENTER("Celownik na środku mapy"),
    GPS_POSITION("Aktualna pozycja GPS"),
    MANUAL("Ręczne wpisanie (Lat / Lon)")
}

private val presetFolders = listOf(
    "Ulubione",
    "Wyprawy górskie",
    "Kemping i Biwak",
    "Parking i Baza",
    "Punkty ostrzegawcze"
)

private val presetColorsHex = listOf(
    "#D32F2F", // Czerwony
    "#E64A19", // Pomarańczowy
    "#388E3C", // Zielony
    "#1976D2", // Niebieski
    "#0288D1", // Błękitny
    "#7B1FA2", // Fioletowy
    "#FBC02D", // Żółty
    "#5D4037"  // Brązowy
)

/**
 * Rozbudowany Menedżer Punktów POI i Wyznaczania Trasy inspirowany Locus Map Pro:
 * - Zakładka 1: Kreator / Edytor punktu POI (Foldery/Grupy, 10 Kategorii, Paleta kolorów,
 *   3 tryby wyboru współrzędnych + wysokość n.p.m., opis, telefon, adres URL).
 * - Zakładka 2: Baza punktów POI (wyszukiwarka, filtrowanie po folderach, dystans od GPS,
 *   edycja punktu, nawigacja bezpośrednio do punktu).
 * - Zakładka 3: Planer trasy OSM (Pieszo / Rower / Samochód).
 */
@Composable
fun WaypointsAndRoutingSheet(
    waypoints: List<Waypoint>,
    gpsStatus: GpsStatus,
    cameraState: MapCameraState,
    activeRoute: RouteResult?,
    isCalculatingRoute: Boolean,
    onSaveWaypoint: (Waypoint) -> Unit,
    onDeleteWaypoint: (Waypoint) -> Unit,
    onFlyToWaypoint: (Waypoint) -> Unit,
    onCalculateRouteToDestination: (lat: Double, lon: Double, profile: RoutingProfile) -> Unit,
    onClearRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    // Pola formularza dodawania / edycji punktu POI w stylu Locus Map
    var editingWaypointId by remember { mutableStateOf(0L) }
    var name by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf(presetFolders.first()) }
    var selectedCategory by remember { mutableStateOf(WaypointCategory.PEAK) }
    var selectedColorHex by remember { mutableStateOf(WaypointCategory.PEAK.colorHex) }
    var coordMode by remember { mutableStateOf(CoordinateSourceMode.MAP_CENTER) }

    var manualLat by remember { mutableStateOf(String.format(Locale.US, "%.5f", cameraState.centerLat)) }
    var manualLon by remember { mutableStateOf(String.format(Locale.US, "%.5f", cameraState.centerLon)) }
    var manualAlt by remember { mutableStateOf(String.format(Locale.US, "%.0f", gpsStatus.altitudeMeters)) }

    var description by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    // Aktualizacja pól współrzędnych przy zmianie trybu źródła lokalizacji
    LaunchedEffect(coordMode, cameraState.centerLat, cameraState.centerLon, gpsStatus.latitude, gpsStatus.longitude) {
        when (coordMode) {
            CoordinateSourceMode.MAP_CENTER -> {
                manualLat = String.format(Locale.US, "%.5f", cameraState.centerLat)
                manualLon = String.format(Locale.US, "%.5f", cameraState.centerLon)
            }
            CoordinateSourceMode.GPS_POSITION -> {
                manualLat = String.format(Locale.US, "%.5f", gpsStatus.latitude)
                manualLon = String.format(Locale.US, "%.5f", gpsStatus.longitude)
                manualAlt = String.format(Locale.US, "%.0f", gpsStatus.altitudeMeters)
            }
            CoordinateSourceMode.MANUAL -> Unit
        }
    }

    // Stan wyszukiwania i filtrowania na liście punktów
    var poiSearchQuery by remember { mutableStateOf("") }
    var selectedFilterFolder by remember { mutableStateOf("Wszystkie") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        text = if (editingWaypointId == 0L) "Nowy POI" else "Edycja POI",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        text = "Baza POI (${waypoints.size})",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = {
                    Text(
                        text = "Trasa OSM",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (selectedTab) {
            0 -> {
                // ZAKŁADKA 1: Rozbudowany kreator punktu POI jak w Locus Map
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nazwa punktu POI (np. Rysy 2499 m, Schronisko PTTK)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Wybór folderu / grupy punktów
                    item {
                        Text(
                            text = "Folder / Grupa punktów (Locus Folders):",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presetFolders.forEach { folder ->
                                FilterChip(
                                    selected = selectedFolder == folder,
                                    onClick = { selectedFolder = folder },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text(folder, fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    // Wybór kategorii punktu POI (10 kategorii)
                    item {
                        Text(
                            text = "Kategoria punktu POI:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WaypointCategory.entries.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = {
                                        selectedCategory = cat
                                        selectedColorHex = cat.colorHex
                                    },
                                    label = { Text(cat.labelPl, fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    // Wybór koloru znacznika na mapie
                    item {
                        Text(
                            text = "Kolor znacznika na mapie:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            presetColorsHex.forEach { hex ->
                                val parsedColor = runCatching {
                                    Color(android.graphics.Color.parseColor(hex))
                                }.getOrDefault(Color.Red)
                                val isSelected = selectedColorHex.equals(hex, ignoreCase = true)

                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(parsedColor)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) Color.White else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColorHex = hex }
                                )
                            }
                        }
                    }

                    // Źródło współrzędnych i wysokości (Celownik mapy / GPS / Ręczna edycja)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Lokalizacja i wysokość n.p.m.:",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CoordinateSourceMode.entries.forEach { mode ->
                                        FilterChip(
                                            selected = coordMode == mode,
                                            onClick = { coordMode = mode },
                                            label = { Text(mode.label, fontSize = 11.sp) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = manualLat,
                                        onValueChange = {
                                            coordMode = CoordinateSourceMode.MANUAL
                                            manualLat = it
                                        },
                                        label = { Text("Szer. (Lat N)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = manualLon,
                                        onValueChange = {
                                            coordMode = CoordinateSourceMode.MANUAL
                                            manualLon = it
                                        },
                                        label = { Text("Dług. (Lon E)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = manualAlt,
                                        onValueChange = { manualAlt = it },
                                        label = { Text("Wys. (m)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(0.8f)
                                    )
                                }
                            }
                        }
                    }

                    // Dodatkowe metadane (Opis, Telefon, Link WWW)
                    item {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Opis / Notatka turystyczna (np. godziny otwarcia, czas podejścia)") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("Telefon (opcjonalnie)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = url,
                                onValueChange = { url = it },
                                label = { Text("Strona WWW / Link") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (editingWaypointId != 0L) {
                                OutlinedButton(
                                    onClick = {
                                        editingWaypointId = 0L
                                        name = ""
                                        description = ""
                                        phone = ""
                                        url = ""
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Anuluj edycję")
                                }
                            }

                            Button(
                                onClick = {
                                    val parsedLat = manualLat.replace(',', '.').toDoubleOrNull() ?: cameraState.centerLat
                                    val parsedLon = manualLon.replace(',', '.').toDoubleOrNull() ?: cameraState.centerLon
                                    val parsedAlt = manualAlt.replace(',', '.').toDoubleOrNull() ?: 0.0

                                    val waypointToSave = Waypoint(
                                        id = editingWaypointId,
                                        name = name.ifBlank { "Punkt: ${selectedCategory.labelPl}" },
                                        folderName = selectedFolder,
                                        description = description.trim(),
                                        latitude = parsedLat,
                                        longitude = parsedLon,
                                        altitudeMeters = parsedAlt,
                                        category = selectedCategory,
                                        colorHex = selectedColorHex,
                                        phone = phone.trim(),
                                        url = url.trim()
                                    )
                                    onSaveWaypoint(waypointToSave)
                                    editingWaypointId = 0L
                                    name = ""
                                    description = ""
                                    phone = ""
                                    url = ""
                                    selectedTab = 1
                                },
                                modifier = Modifier.weight(2f)
                            ) {
                                Icon(Icons.Default.AddLocationAlt, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (editingWaypointId == 0L) "Zapisz punkt POI" else "Zaktualizuj punkt POI")
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            1 -> {
                // ZAKŁADKA 2: Baza punktów POI z wyszukiwarką, filtrami folderów i akcjami
                val allFolders = remember(waypoints) {
                    listOf("Wszystkie") + waypoints.map { it.folderName }.distinct()
                }
                val filteredWaypoints = remember(waypoints, poiSearchQuery, selectedFilterFolder) {
                    val q = poiSearchQuery.trim().lowercase(Locale.getDefault())
                    waypoints.filter { wpt ->
                        val folderOk = selectedFilterFolder == "Wszystkie" || wpt.folderName == selectedFilterFolder
                        val queryOk = q.isEmpty() ||
                            wpt.name.lowercase(Locale.getDefault()).contains(q) ||
                            wpt.description.lowercase(Locale.getDefault()).contains(q) ||
                            wpt.category.labelPl.lowercase(Locale.getDefault()).contains(q)
                        folderOk && queryOk
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = poiSearchQuery,
                        onValueChange = { poiSearchQuery = it },
                        placeholder = { Text("Szukaj punktu POI po nazwie, opisie lub kategorii...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (poiSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { poiSearchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allFolders.forEach { folder ->
                            FilterChip(
                                selected = selectedFilterFolder == folder,
                                onClick = { selectedFilterFolder = folder },
                                label = { Text(folder, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(filteredWaypoints, key = { it.id }) { wpt ->
                            val distMeters = GpxSerializer.haversineDistanceMeters(
                                gpsStatus.latitude,
                                gpsStatus.longitude,
                                wpt.latitude,
                                wpt.longitude
                            )
                            val distText = if (distMeters >= 1000.0) {
                                String.format(Locale.US, "%.2f km od GPS", distMeters / 1000.0)
                            } else {
                                String.format(Locale.US, "%.0f m od GPS", distMeters)
                            }

                            val dotColor = runCatching {
                                Color(android.graphics.Color.parseColor(wpt.colorHex))
                            }.getOrDefault(Color(0xFFD32F2F))

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
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clip(CircleShape)
                                                    .background(dotColor)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = wpt.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                                Text(
                                                    text = "[${wpt.folderName}] • ${wpt.category.labelPl} • $distText",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = String.format(
                                                        Locale.US,
                                                        "N %.5f° E %.5f° • %.0f m n.p.m.",
                                                        wpt.latitude,
                                                        wpt.longitude,
                                                        wpt.altitudeMeters
                                                    ),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (wpt.description.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = wpt.description,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }

                                        Row {
                                            IconButton(
                                                onClick = {
                                                    onCalculateRouteToDestination(
                                                        wpt.latitude,
                                                        wpt.longitude,
                                                        RoutingProfile.HIKING
                                                    )
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Route,
                                                    contentDescription = "Wyznacz trasę do punktu"
                                                )
                                            }
                                            IconButton(onClick = { onFlyToWaypoint(wpt) }) {
                                                Icon(
                                                    imageVector = Icons.Default.MyLocation,
                                                    contentDescription = "Pokaż na mapie"
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    editingWaypointId = wpt.id
                                                    name = wpt.name
                                                    selectedFolder = wpt.folderName
                                                    selectedCategory = wpt.category
                                                    selectedColorHex = wpt.colorHex
                                                    coordMode = CoordinateSourceMode.MANUAL
                                                    manualLat = String.format(Locale.US, "%.5f", wpt.latitude)
                                                    manualLon = String.format(Locale.US, "%.5f", wpt.longitude)
                                                    manualAlt = String.format(Locale.US, "%.0f", wpt.altitudeMeters)
                                                    description = wpt.description
                                                    phone = wpt.phone
                                                    url = wpt.url
                                                    selectedTab = 0
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edytuj punkt POI"
                                                )
                                            }
                                            IconButton(onClick = { onDeleteWaypoint(wpt) }) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
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
            }

            2 -> {
                // ZAKŁADKA 3: Wyznaczanie trasy OSM do celownika na mapie
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Wyznaczanie Trasy (z pozycji GPS do celownika mapy)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                onCalculateRouteToDestination(
                                    cameraState.centerLat,
                                    cameraState.centerLon,
                                    RoutingProfile.HIKING
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isCalculatingRoute
                        ) {
                            Icon(Icons.Default.Hiking, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pieszo", fontSize = 12.sp)
                        }
                        FilledTonalButton(
                            onClick = {
                                onCalculateRouteToDestination(
                                    cameraState.centerLat,
                                    cameraState.centerLon,
                                    RoutingProfile.BICYCLE
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isCalculatingRoute
                        ) {
                            Icon(Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rower", fontSize = 12.sp)
                        }
                        FilledTonalButton(
                            onClick = {
                                onCalculateRouteToDestination(
                                    cameraState.centerLat,
                                    cameraState.centerLon,
                                    RoutingProfile.CAR
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isCalculatingRoute
                        ) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Auto", fontSize = 12.sp)
                        }
                    }

                    if (isCalculatingRoute) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Obliczanie optymalnej trasy OpenStreetMap...", fontSize = 12.sp)
                        }
                    }

                    if (activeRoute != null) {
                        Spacer(modifier = Modifier.height(10.dp))
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
                }
            }
        }
    }
}
