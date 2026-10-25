package com.opennavi.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opennavi.data.gpx.GpxSerializer
import com.opennavi.domain.model.GpsStatus
import com.opennavi.domain.model.MapOrientationMode
import com.opennavi.ui.MapCameraState
import java.util.Locale

/**
 * Górny pasek telemetryczny w stylu Locus Map Pro:
 * prezentuje liczbę satelitów GNSS, dokładność GPS, wysokość n.p.m., aktualną prędkość
 * oraz współrzędne środka mapy / odległość celownika od pozycji GPS.
 */
@Composable
fun LocusTopTelemetryBar(
    gpsStatus: GpsStatus,
    cameraState: MapCameraState,
    onOpenStylePicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val distanceFromGpsMeters = GpxSerializer.haversineDistanceMeters(
        gpsStatus.latitude,
        gpsStatus.longitude,
        cameraState.centerLat,
        cameraState.centerLon
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xD9161B22)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Status satelitów GNSS
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SatelliteAlt,
                        contentDescription = "GNSS",
                        tint = if (gpsStatus.hasValidFix) Color(0xFF4CAF50) else Color(0xFFFFB300),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${gpsStatus.satellitesUsedInFix}/${gpsStatus.satellitesVisible} SAT",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (gpsStatus.hasValidFix) {
                            String.format(Locale.US, "±%.0f m", gpsStatus.accuracyMeters)
                        } else {
                            "Szukanie GPS..."
                        },
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp
                    )
                }

                // Wysokość n.p.m. oraz prędkość
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terrain,
                        contentDescription = "Wysokość",
                        tint = Color(0xFF81C784),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format(Locale.US, "%.0f m n.p.m.", gpsStatus.altitudeMeters),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f km/h", gpsStatus.speedKmH),
                        color = Color(0xFF64B5F6),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onOpenStylePicker,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Warstwy mapy OSM",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Dolny wiersz: Współrzędne środka mapy + odległość od pozycji GPS (gdy przesunięto mapę)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format(
                        Locale.US,
                        "N %.5f°  E %.5f°  |  Z:%.1f",
                        cameraState.centerLat,
                        cameraState.centerLon,
                        cameraState.zoom
                    ),
                    color = Color(0xFFCFD8DC),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                if (!cameraState.followGps && distanceFromGpsMeters > 10.0) {
                    val distLabel = if (distanceFromGpsMeters >= 1000.0) {
                        String.format(Locale.US, "Celownik: %.2f km", distanceFromGpsMeters / 1000.0)
                    } else {
                        String.format(Locale.US, "Celownik: %.0f m", distanceFromGpsMeters)
                    }
                    Text(
                        text = distLabel,
                        color = Color(0xFFFFCA28),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    val modeText = when (cameraState.orientationMode) {
                        MapOrientationMode.NORTH_UP -> "Północ u góry"
                        MapOrientationMode.COMPASS_ROTATE -> "Obrót: Kompas"
                        MapOrientationMode.GPS_BEARING -> "Obrót: Ruch GPS"
                    }
                    Text(
                        text = modeText,
                        color = Color(0xFF80CBC4),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Róża kompasu w stylu Locus Map pokazująca aktualny azymut magnetyczny i kierunek północy.
 */
@Composable
fun LocusCompassOverlay(
    azimuthDegrees: Float,
    orientationMode: MapOrientationMode,
    onClickResetNorth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .clickable { onClickResetNorth() },
        color = Color(0xD9161B22),
        shape = CircleShape,
        shadowElevation = 6.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(46.dp)) {
                val radius = size.minDimension / 2f
                drawCircle(
                    color = Color(0xFF455A64),
                    radius = radius,
                    style = Stroke(width = 2.dp.toPx())
                )

                rotate(degrees = -azimuthDegrees) {
                    // Czerwona igła Północy (N)
                    val northPath = Path().apply {
                        moveTo(center.x, center.y - radius * 0.78f)
                        lineTo(center.x - 5.dp.toPx(), center.y)
                        lineTo(center.x + 5.dp.toPx(), center.y)
                        close()
                    }
                    drawPath(northPath, color = Color(0xFFE53935))

                    // Biała igła Południa (S)
                    val southPath = Path().apply {
                        moveTo(center.x, center.y + radius * 0.78f)
                        lineTo(center.x - 5.dp.toPx(), center.y)
                        lineTo(center.x + 5.dp.toPx(), center.y)
                        close()
                    }
                    drawPath(southPath, color = Color(0xFFECEFF1))
                }
            }

            Text(
                text = "${azimuthDegrees.toInt()}°",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color(0xAA000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            )
        }
    }
}

/**
 * Celownik centralny mapy (Crosshair) używany w Locus Map do precyzyjnego wskazywania
 * szczytów, dodawania punktów Waypoint oraz planowania trasy.
 */
@Composable
fun LocusMapCrosshair(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    Canvas(modifier = modifier.size(44.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        val gap = 6.dp.toPx()
        val half = size.minDimension / 2f

        // Okrąg celownika
        drawCircle(
            color = Color(0xCC263238),
            radius = 10.dp.toPx(),
            style = Stroke(width = strokeWidth)
        )

        // Linie krzyża
        drawLine(
            color = Color(0xFFD32F2F),
            start = Offset(0f, half),
            end = Offset(half - gap, half),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = Color(0xFFD32F2F),
            start = Offset(half + gap, half),
            end = Offset(size.width, half),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = Color(0xFFD32F2F),
            start = Offset(half, 0f),
            end = Offset(half, half - gap),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = Color(0xFFD32F2F),
            start = Offset(half, half + gap),
            end = Offset(half, size.height),
            strokeWidth = strokeWidth
        )
    }
}
