package com.opennavi.domain.model

/**
 * Tryb orientacji mapy inspirowany Locus Map:
 * - NORTH_UP: Północ zawsze u góry ekranu
 * - COMPASS_ROTATE: Mapa obraca się zgodnie z czujnikiem magnetycznym / żyroskopem
 * - GPS_BEARING: Mapa obraca się zgodnie z kierunkiem ruchu GPS
 */
enum class MapOrientationMode {
    NORTH_UP,
    COMPASS_ROTATE,
    GPS_BEARING
}

/**
 * Styl mapy OpenStreetMap w MapLibre (w 100% darmowe źródła wektorowe i rastrowe).
 */
enum class MapStyleTheme(
    val title: String,
    val subtitle: String,
    val styleUrl: String,
    val isInlineJson: Boolean = false
) {
    OUTDOOR_LIBERTY(
        title = "OSM Liberty (Turystyczna 3D)",
        subtitle = "Wektorowa mapa z budynkami 3D, lasami i punktami POI",
        styleUrl = "https://tiles.openfreemap.org/styles/liberty"
    ),
    BRIGHT_TOPO(
        title = "OSM Bright (Wyraźna / Drogowa)",
        subtitle = "Wektorowa mapa o wysokim kontraście dróg i miast",
        styleUrl = "https://tiles.openfreemap.org/styles/bright"
    ),
    OPENTOPOMAP(
        title = "OpenTopoMap (Górska / Poziomice)",
        subtitle = "Klasyczna mapa topograficzna z poziomicami i cieniowaniem wzgórz",
        styleUrl = """{"version":8,"sources":{"opentopomap":{"type":"raster","tiles":["https://tile.opentopomap.org/{z}/{x}/{y}.png"],"tileSize":256,"maxzoom":17}},"layers":[{"id":"opentopomap-layer","type":"raster","source":"opentopomap"}]}""",
        isInlineJson = true
    ),
    CYCLOSM(
        title = "CyclOSM (Rowerowa i Szlaki)",
        subtitle = "Specjalistyczna mapa tras rowerowych, szlaków pieszych i nawierzchni",
        styleUrl = """{"version":8,"sources":{"cyclosm":{"type":"raster","tiles":["https://a.tile-cyclosm.openstreetmap.fr/cyclosm/{z}/{x}/{y}.png"],"tileSize":256,"maxzoom":19}},"layers":[{"id":"cyclosm-layer","type":"raster","source":"cyclosm"}]}""",
        isInlineJson = true
    ),
    OSM_STANDARD(
        title = "OpenStreetMap Standard (Carto)",
        subtitle = "Oficjalny klasyczny widok mapy OpenStreetMap.org",
        styleUrl = """{"version":8,"sources":{"osm-carto":{"type":"raster","tiles":["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],"tileSize":256,"maxzoom":19}},"layers":[{"id":"osm-carto-layer","type":"raster","source":"osm-carto"}]}""",
        isInlineJson = true
    ),
    DARK_POSITRON(
        title = "OSM Positron (Minimalistyczna)",
        subtitle = "Jasna, stonowana mapa wektorowa idealna pod ślady GPX",
        styleUrl = "https://tiles.openfreemap.org/styles/positron"
    )
}

/**
 * Szczegóły pojedynczego satelity GNSS odbieranego przez urządzenie.
 */
data class GnssSatelliteDetail(
    val svid: Int,
    val constellationName: String,
    val cn0DbHz: Float,
    val usedInFix: Boolean,
    val elevationDegrees: Float,
    val azimuthDegrees: Float
)

/**
 * Stan sensora GPS / GNSS oraz kompasu w czasie rzeczywistym.
 */
data class GpsStatus(
    val hasPermission: Boolean = false,
    val isGpsEnabled: Boolean = false,
    val latitude: Double = 52.2297,   // Domyślnie Warszawa / Polska
    val longitude: Double = 21.0122,
    val altitudeMeters: Double = 0.0,
    val hasBarometer: Boolean = false,
    val speedMetersPerSec: Float = 0f,
    val accuracyMeters: Float = 0f,
    val bearingDegrees: Float = 0f,
    val compassAzimuthDegrees: Float = 0f,
    val satellitesVisible: Int = 0,
    val satellitesUsedInFix: Int = 0,
    val satelliteDetails: List<GnssSatelliteDetail> = emptyList(),
    val locationProviderName: String = "Brak",
    val lastFixTimestamp: Long = 0L
) {
    val hasValidFix: Boolean
        get() = lastFixTimestamp > 0L && accuracyMeters > 0f

    val speedKmH: Float
        get() = speedMetersPerSec * 3.6f
}

/**
 * Status pobrania mapy offline dla wybranego państwa.
 */
enum class OfflineMapState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED_UP_TO_DATE,
    UPDATE_AVAILABLE,
    ERROR
}

/**
 * Model państwa dostępnego do pobrania i aktualizacji offline (OpenStreetMap PMTiles / Vector).
 */
data class CountryOfflineMap(
    val countryCode: String,
    val countryNamePl: String,
    val regionName: String,
    val boundingBox: BoundingBox,
    val downloadUrl: String,
    val estimatedSizeMB: Long,
    val remoteVersionTag: String = "",
    val localVersionTag: String = "",
    val localFilePath: String? = null,
    val lastUpdatedEpochMillis: Long = 0L,
    val downloadProgressPercent: Int = 0,
    val state: OfflineMapState = OfflineMapState.NOT_DOWNLOADED
)

data class BoundingBox(
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double
) {
    val centerLat: Double get() = (minLat + maxLat) / 2.0
    val centerLon: Double get() = (minLon + maxLon) / 2.0
}

/**
 * Pojedynczy punkt śladu GPX.
 */
data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val timestampMillis: Long,
    val speedMetersPerSec: Float = 0f,
    val accuracyMeters: Float = 0f
)

/**
 * Zapisany lub aktualnie nagrywany ślad GPX (inspirowany Locus Map Track Manager).
 */
data class RecordedTrack(
    val id: Long = 0L,
    val name: String,
    val description: String = "",
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val totalDistanceMeters: Double,
    val movingTimeMillis: Long,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val maxSpeedMetersPerSec: Float,
    val minAltitudeMeters: Double,
    val maxAltitudeMeters: Double,
    val points: List<TrackPoint> = emptyList(),
    val isVisibleOnMap: Boolean = true,
    val colorHex: String = "#E53935"
) {
    val averageSpeedKmH: Double
        get() = if (movingTimeMillis > 1000L) {
            (totalDistanceMeters / (movingTimeMillis / 1000.0)) * 3.6
        } else 0.0
}

/**
 * Kategoria punktu Waypoint / POI w stylu Locus Map.
 */
enum class WaypointCategory(val labelPl: String, val colorHex: String) {
    PEAK("Szczyt / Góra", "#D32F2F"),
    VIEWPOINT("Punkt widokowy", "#E64A19"),
    SHELTER("Schronisko / Chatka", "#388E3C"),
    CAMP("Biwak / Pole namiotowe", "#689F38"),
    PARKING("Parking / Start szlaku", "#1976D2"),
    WATER("Źródło wody / Studnia", "#0288D1"),
    FOOD("Restauracja / Sklep", "#F57C00"),
    ATTRACTION("Atrakcja / Zabytek", "#7B1FA2"),
    WARNING("Uwaga / Niebezpieczeństwo", "#FBC02D"),
    CUSTOM("Własny punkt", "#5D4037")
}

/**
 * Punkt użytkownika (Waypoint / POI) zapisany na mapie (inspirowany Locus Map Point Manager).
 */
data class Waypoint(
    val id: Long = 0L,
    val name: String,
    val folderName: String = "Ulubione",
    val description: String = "",
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double = 0.0,
    val category: WaypointCategory = WaypointCategory.CUSTOM,
    val colorHex: String = category.colorHex,
    val phone: String = "",
    val url: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

/**
 * Profil wyznaczania trasy (Routing).
 */
enum class RoutingProfile(val labelPl: String, val osrmProfile: String) {
    HIKING("Pieszy / Górski (Szlak)", "foot"),
    BICYCLE("Rowerowy / Gravel", "bike"),
    CAR("Samochodowy", "car")
}

/**
 * Wynik wyznaczonej trasy wraz z geometrią i statystykami.
 */
data class RouteResult(
    val profile: RoutingProfile,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val geometryPoints: List<Pair<Double, Double>>, // Lista (Lat, Lon)
    val instructions: List<String> = emptyList()
)
