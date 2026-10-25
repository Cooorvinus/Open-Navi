package com.opennavi.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.opennavi.data.gpx.GpxSerializer
import com.opennavi.data.repository.OfflineMapRepository
import com.opennavi.data.repository.TrackAndWaypointRepository
import com.opennavi.di.AppContainer
import com.opennavi.domain.model.CountryOfflineMap
import com.opennavi.domain.model.GpsStatus
import com.opennavi.domain.model.MapOrientationMode
import com.opennavi.domain.model.MapStyleTheme
import com.opennavi.domain.model.RecordedTrack
import com.opennavi.domain.model.RouteResult
import com.opennavi.domain.model.RoutingProfile
import com.opennavi.domain.model.Waypoint
import com.opennavi.domain.model.WaypointCategory
import com.opennavi.service.ActiveRecordingSession
import com.opennavi.service.LocationAndCompassTracker
import com.opennavi.service.TrackRecordingController
import com.opennavi.service.TrackRecordingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MapCameraState(
    val centerLat: Double = 52.2297,
    val centerLon: Double = 21.0122,
    val zoom: Double = 12.5,
    val tiltDegrees: Double = 0.0,
    val bearingDegrees: Double = 0.0,
    val followGps: Boolean = true,
    val orientationMode: MapOrientationMode = MapOrientationMode.NORTH_UP,
    val styleTheme: MapStyleTheme = MapStyleTheme.OUTDOOR_LIBERTY
)

class OpenNaviViewModel(
    private val appContext: Context,
    private val locationTracker: LocationAndCompassTracker,
    private val recordingController: TrackRecordingController,
    private val offlineMapRepository: OfflineMapRepository,
    private val trackAndWaypointRepository: TrackAndWaypointRepository
) : ViewModel() {

    val gpsStatus: StateFlow<GpsStatus> = locationTracker.gpsStatus

    val recordingSession: StateFlow<ActiveRecordingSession> = recordingController.session

    val countriesOfflineMaps: StateFlow<List<CountryOfflineMap>> =
        offlineMapRepository.observeCountriesWithStatus()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedTracks: StateFlow<List<RecordedTrack>> =
        trackAndWaypointRepository.observeAllTracks()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedWaypoints: StateFlow<List<Waypoint>> =
        trackAndWaypointRepository.observeAllWaypoints()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _cameraState = MutableStateFlow(MapCameraState())
    val cameraState: StateFlow<MapCameraState> = _cameraState.asStateFlow()

    private val _activeRoute = MutableStateFlow<RouteResult?>(null)
    val activeRoute: StateFlow<RouteResult?> = _activeRoute.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _isCalculatingRoute = MutableStateFlow(false)
    val isCalculatingRoute: StateFlow<Boolean> = _isCalculatingRoute.asStateFlow()

    fun startSensors() {
        locationTracker.startTracking()
    }

    fun updateMapCenterFromGesture(lat: Double, lon: Double, zoom: Double) {
        _cameraState.update {
            it.copy(
                centerLat = lat,
                centerLon = lon,
                zoom = zoom,
                followGps = false
            )
        }
    }

    fun centerOnGpsAndCycleOrientation() {
        val gps = gpsStatus.value
        _cameraState.update { current ->
            val nextMode = if (!current.followGps) {
                current.orientationMode
            } else {
                when (current.orientationMode) {
                    MapOrientationMode.NORTH_UP -> MapOrientationMode.COMPASS_ROTATE
                    MapOrientationMode.COMPASS_ROTATE -> MapOrientationMode.GPS_BEARING
                    MapOrientationMode.GPS_BEARING -> MapOrientationMode.NORTH_UP
                }
            }
            current.copy(
                centerLat = gps.latitude,
                centerLon = gps.longitude,
                followGps = true,
                orientationMode = nextMode
            )
        }
    }

    fun toggle3DTilt() {
        _cameraState.update { current ->
            val nextTilt = if (current.tiltDegrees < 10.0) 48.0 else 0.0
            current.copy(tiltDegrees = nextTilt)
        }
    }

    fun zoomBy(delta: Double) {
        _cameraState.update { current ->
            current.copy(zoom = (current.zoom + delta).coerceIn(2.0, 20.0))
        }
    }

    fun selectMapStyle(theme: MapStyleTheme) {
        _cameraState.update { it.copy(styleTheme = theme) }
        showMessage("Zmieniono styl mapy: ${theme.title}")
    }

    fun flyToLocation(lat: Double, lon: Double, zoom: Double = 14.0) {
        _cameraState.update {
            it.copy(
                centerLat = lat,
                centerLon = lon,
                zoom = zoom,
                followGps = false
            )
        }
    }

    fun startTrackRecording() {
        val intent = Intent(appContext, TrackRecordingService::class.java).apply {
            action = TrackRecordingService.ACTION_START
        }
        appContext.startForegroundService(intent)
        showMessage("Rozpoczęto nagrywanie śladu GPX")
    }

    fun pauseOrResumeTrackRecording() {
        val session = recordingSession.value
        if (!session.isRecording) return
        val action = if (session.isPaused) {
            TrackRecordingService.ACTION_RESUME
        } else {
            TrackRecordingService.ACTION_PAUSE
        }
        val intent = Intent(appContext, TrackRecordingService::class.java).apply {
            this.action = action
        }
        appContext.startService(intent)
    }

    fun stopAndSaveTrackRecording(customName: String? = null) {
        val session = recordingSession.value
        val stopIntent = Intent(appContext, TrackRecordingService::class.java).apply {
            action = TrackRecordingService.ACTION_STOP
        }
        appContext.startService(stopIntent)

        viewModelScope.launch {
            val timestampStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date(session.startTimeMillis.takeIf { it > 0 } ?: System.currentTimeMillis()))
            val trackName = customName?.takeIf { it.isNotBlank() } ?: "Ślad $timestampStr"

            val builtTrack = GpxSerializer.buildTrackStatistics(
                name = trackName,
                points = session.points
            )
            trackAndWaypointRepository.saveTrack(builtTrack)
            recordingController.reset()
            showMessage("Zapisano ślad: $trackName")
        }
    }

    fun toggleSavedTrackVisibility(track: RecordedTrack) {
        viewModelScope.launch {
            trackAndWaypointRepository.toggleTrackVisibility(track)
        }
    }

    fun deleteSavedTrack(track: RecordedTrack) {
        viewModelScope.launch {
            trackAndWaypointRepository.deleteTrack(track)
            showMessage("Usunięto ślad: ${track.name}")
        }
    }

    fun exportTrackToStream(track: RecordedTrack, outputStream: OutputStream) {
        viewModelScope.launch {
            runCatching {
                GpxSerializer.exportTrackToGpx(
                    track = track,
                    waypoints = savedWaypoints.value,
                    outputStream = outputStream
                )
            }.onSuccess {
                showMessage("Wyeksportowano plik GPX: ${track.name}")
            }.onFailure { err ->
                showMessage("Błąd eksportu GPX: ${err.localizedMessage}")
            }
        }
    }

    fun importGpxFromStream(inputStream: InputStream) {
        viewModelScope.launch {
            runCatching {
                val imported = GpxSerializer.importFromGpx(inputStream)
                imported.track?.let { trackAndWaypointRepository.saveTrack(it) }
                imported.waypoints.forEach { trackAndWaypointRepository.saveWaypoint(it) }
                imported
            }.onSuccess { imported ->
                val trkInfo = if (imported.track != null) "1 ślad (${imported.track.points.size} pkt)" else "0 śladów"
                showMessage("Zaimportowano GPX: $trkInfo, ${imported.waypoints.size} punktów POI")
                imported.track?.points?.firstOrNull()?.let { firstPt ->
                    flyToLocation(firstPt.latitude, firstPt.longitude, 13.5)
                }
            }.onFailure { err ->
                showMessage("Błąd importu GPX: ${err.localizedMessage}")
            }
        }
    }

    fun addWaypointAtMapCenter(
        name: String,
        description: String,
        category: WaypointCategory,
        useGpsLocation: Boolean
    ) {
        viewModelScope.launch {
            val gps = gpsStatus.value
            val cam = cameraState.value
            val lat = if (useGpsLocation) gps.latitude else cam.centerLat
            val lon = if (useGpsLocation) gps.longitude else cam.centerLon
            val alt = if (useGpsLocation) gps.altitudeMeters else 0.0

            val waypoint = Waypoint(
                name = name.ifBlank { "Punkt ${category.labelPl}" },
                description = description,
                latitude = lat,
                longitude = lon,
                altitudeMeters = alt,
                category = category
            )
            trackAndWaypointRepository.saveWaypoint(waypoint)
            showMessage("Dodano punkt: ${waypoint.name}")
        }
    }

    fun deleteWaypoint(waypoint: Waypoint) {
        viewModelScope.launch {
            trackAndWaypointRepository.deleteWaypoint(waypoint)
            showMessage("Usunięto punkt: ${waypoint.name}")
        }
    }

    fun downloadOrUpdateCountryMap(countryCode: String) {
        viewModelScope.launch {
            val result = offlineMapRepository.downloadOrUpdateCountry(countryCode) { country ->
                triggerMapLibreOfflineRegionSync(country)
            }
            result.onSuccess { updated ->
                showMessage("Mapa offline (${updated.countryNamePl}) zapisana i zaktualizowana!")
            }.onFailure { err ->
                showMessage("Błąd pobierania mapy: ${err.localizedMessage}")
            }
        }
    }

    fun checkCountryMapUpdates() {
        viewModelScope.launch {
            showMessage("Sprawdzanie aktualizacji map OpenStreetMap...")
            val count = offlineMapRepository.checkForUpdates()
            if (count > 0) {
                showMessage("Znaleziono aktualizacje dla $count państw!")
            } else {
                showMessage("Wszystkie pobrane mapy państw są aktualne.")
            }
        }
    }

    fun simulateRemoteMapUpdate(countryCode: String) {
        viewModelScope.launch {
            offlineMapRepository.markUpdateAvailableForTesting(countryCode)
            showMessage("Wykryto nową wersję OSM dla państwa $countryCode")
        }
    }

    fun deleteCountryOfflineMap(countryCode: String) {
        viewModelScope.launch {
            offlineMapRepository.deleteCountryMap(countryCode)
            showMessage("Usunięto lokalną mapę państwa $countryCode")
        }
    }

    private fun triggerMapLibreOfflineRegionSync(country: CountryOfflineMap) {
        runCatching {
            val offlineManager = OfflineManager.getInstance(appContext)
            val bounds = LatLngBounds.from(
                country.boundingBox.maxLat,
                country.boundingBox.maxLon,
                country.boundingBox.minLat,
                country.boundingBox.minLon
            )
            val definition = OfflineTilePyramidRegionDefinition(
                cameraState.value.styleTheme.styleUrl,
                bounds,
                5.0,
                12.0,
                appContext.resources.displayMetrics.density
            )
            val metadata = country.countryCode.toByteArray(Charsets.UTF_8)
            offlineManager.createOfflineRegion(
                definition,
                metadata,
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(offlineRegion: OfflineRegion) {
                        offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    }

                    override fun onError(error: String) = Unit
                }
            )
        }
    }

    fun calculateRouteFromGpsToMapCenter(profile: RoutingProfile) {
        val gps = gpsStatus.value
        val target = cameraState.value
        _isCalculatingRoute.value = true

        viewModelScope.launch {
            val res = trackAndWaypointRepository.calculateRoute(
                startLat = gps.latitude,
                startLon = gps.longitude,
                endLat = target.centerLat,
                endLon = target.centerLon,
                profile = profile
            )
            _isCalculatingRoute.value = false
            res.onSuccess { route ->
                _activeRoute.value = route
                val distKm = String.format(Locale.US, "%.1f km", route.distanceMeters / 1000.0)
                showMessage("Wyznaczono trasę (${profile.labelPl}): $distKm")
            }.onFailure { err ->
                showMessage("Nie udało się wyznaczyć trasy: ${err.localizedMessage}")
            }
        }
    }

    fun clearActiveRoute() {
        _activeRoute.value = null
    }

    fun dismissBannerMessage() {
        _statusBannerMessage.value = null
    }

    private fun showMessage(msg: String) {
        _statusBannerMessage.value = msg
    }

    companion object {
        fun provideFactory(appContext: Context, container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return OpenNaviViewModel(
                        appContext = appContext.applicationContext,
                        locationTracker = container.locationTracker,
                        recordingController = container.recordingController,
                        offlineMapRepository = container.offlineMapRepository,
                        trackAndWaypointRepository = container.trackAndWaypointRepository
                    ) as T
                }
            }
    }
}
