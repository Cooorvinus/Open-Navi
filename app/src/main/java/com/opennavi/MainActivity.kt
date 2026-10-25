package com.opennavi

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.opennavi.domain.model.RecordedTrack
import com.opennavi.ui.OpenNaviScreen
import com.opennavi.ui.OpenNaviViewModel
import dagger.hilt.android.AndroidEntryPoint

private val OpenNaviDarkColorScheme = darkColorScheme(
    primary = Color(0xFF66BB6A),
    secondary = Color(0xFF4FC3F7),
    tertiary = Color(0xFFFFCA28),
    background = Color(0xFF101418),
    surface = Color(0xFF161B22)
)

private val OpenNaviLightColorScheme = lightColorScheme(
    primary = Color(0xFF1B5E20),
    secondary = Color(0xFF0277BD),
    tertiary = Color(0xFFEF6C00)
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: OpenNaviViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Natywna obsługa Edge-to-Edge dla Androida 15-17 (API 35-37)
        enableEdgeToEdge()

        setContent {
            val darkTheme = isSystemInDarkTheme()
            var pendingTrackForExport by remember { mutableStateOf<RecordedTrack?>(null) }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) {
                viewModel.startSensors()
            }

            // Selektor pliku do importu GPX
            val importGpxLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    contentResolver.openInputStream(uri)?.let { inputStream ->
                        viewModel.importGpxFromStream(inputStream)
                    }
                }
            }

            // Selektor pliku do zapisu / eksportu GPX
            val exportGpxLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/gpx+xml")
            ) { uri ->
                val track = pendingTrackForExport
                if (uri != null && track != null) {
                    contentResolver.openOutputStream(uri)?.let { outputStream ->
                        viewModel.exportTrackToStream(track, outputStream)
                    }
                }
            }

            LaunchedEffect(Unit) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                )
            }

            MaterialTheme(
                colorScheme = if (darkTheme) OpenNaviDarkColorScheme else OpenNaviLightColorScheme
            ) {
                OpenNaviScreen(
                    viewModel = viewModel,
                    onRequestImportGpxFile = {
                        importGpxLauncher.launch(arrayOf("*/*"))
                    },
                    onRequestExportGpxFile = { track ->
                        pendingTrackForExport = track
                        val safeName = track.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                        exportGpxLauncher.launch("${safeName}.gpx")
                    }
                )
            }
        }
    }
}
