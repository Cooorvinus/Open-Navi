package com.opennavi.data.repository

import com.opennavi.data.local.TrackDao
import com.opennavi.data.local.TrackEntity
import com.opennavi.data.local.WaypointDao
import com.opennavi.data.local.WaypointEntity
import com.opennavi.domain.model.RecordedTrack
import com.opennavi.domain.model.RoutingProfile
import com.opennavi.domain.model.RouteResult
import com.opennavi.domain.model.TrackPoint
import com.opennavi.domain.model.Waypoint
import com.opennavi.domain.model.WaypointCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackAndWaypointRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val waypointDao: WaypointDao,
    private val okHttpClient: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeAllTracks(): Flow<List<RecordedTrack>> =
        trackDao.observeAllTracks().map { entities ->
            entities.map { it.toDomain() }
        }

    fun observeAllWaypoints(): Flow<List<Waypoint>> =
        waypointDao.observeAllWaypoints().map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun saveTrack(track: RecordedTrack): Long = withContext(Dispatchers.IO) {
        trackDao.insertTrack(track.toEntity())
    }

    suspend fun toggleTrackVisibility(track: RecordedTrack) = withContext(Dispatchers.IO) {
        val updated = track.copy(isVisibleOnMap = !track.isVisibleOnMap)
        trackDao.updateTrack(updated.toEntity())
    }

    suspend fun deleteTrack(track: RecordedTrack) = withContext(Dispatchers.IO) {
        trackDao.deleteTrack(track.toEntity())
    }

    suspend fun saveWaypoint(waypoint: Waypoint): Long = withContext(Dispatchers.IO) {
        waypointDao.insertWaypoint(waypoint.toEntity())
    }

    suspend fun deleteWaypoint(waypoint: Waypoint) = withContext(Dispatchers.IO) {
        waypointDao.deleteWaypoint(waypoint.toEntity())
    }

    /**
     * Wyznacza trasę za pomocą darmowego serwera routingu OpenStreetMap (OSRM / FOSSGIS)
     * dla profili: Pieszy (Hiking), Rowerowy (Bicycle) oraz Samochodowy (Car).
     */
    suspend fun calculateRoute(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        profile: RoutingProfile
    ): Result<RouteResult> = withContext(Dispatchers.IO) {
        runCatching {
            val baseServer = when (profile) {
                RoutingProfile.HIKING -> "https://routing.openstreetmap.de/routed-foot/route/v1/foot"
                RoutingProfile.BICYCLE -> "https://routing.openstreetmap.de/routed-bike/route/v1/driving"
                RoutingProfile.CAR -> "https://routing.openstreetmap.de/routed-car/route/v1/driving"
            }

            val coords = String.format(
                Locale.US,
                "%.6f,%.6f;%.6f,%.6f",
                startLon,
                startLat,
                endLon,
                endLat
            )
            val url = "$baseServer/$coords?overview=full&geometries=geojson&steps=true"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "OpenNavi-Android/1.0 (OpenStreetMap Routing)")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Błąd serwera routingu OSM (${response.code})")
                }
                val body = response.body?.string() ?: error("Pusta odpowiedź serwera routingu")
                val root = json.parseToJsonElement(body).jsonObject
                val routes = root["routes"]?.jsonArray
                if (routes.isNullOrEmpty()) {
                    error("Nie znaleziono trasy dla wybranego odcinka")
                }

                val firstRoute = routes[0].jsonObject
                val distanceMeters = firstRoute["distance"]?.jsonPrimitive?.double ?: 0.0
                val durationSeconds = firstRoute["duration"]?.jsonPrimitive?.double ?: 0.0

                val coordinatesArray = firstRoute["geometry"]
                    ?.jsonObject
                    ?.get("coordinates")
                    ?.jsonArray
                    ?: error("Brak geometrii trasy")

                val geometryPoints = coordinatesArray.map { coordElem ->
                    val pair = coordElem.jsonArray
                    val lon = pair[0].jsonPrimitive.double
                    val lat = pair[1].jsonPrimitive.double
                    lat to lon
                }

                RouteResult(
                    profile = profile,
                    distanceMeters = distanceMeters,
                    durationSeconds = durationSeconds,
                    geometryPoints = geometryPoints
                )
            }
        }
    }

    private fun TrackEntity.toDomain(): RecordedTrack {
        val points = runCatching {
            json.decodeFromString<List<TrackPoint>>(pointsJson)
        }.getOrDefault(emptyList())

        return RecordedTrack(
            id = id,
            name = name,
            description = description,
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis,
            totalDistanceMeters = totalDistanceMeters,
            movingTimeMillis = movingTimeMillis,
            elevationGainMeters = elevationGainMeters,
            elevationLossMeters = elevationLossMeters,
            maxSpeedMetersPerSec = maxSpeedMetersPerSec,
            minAltitudeMeters = minAltitudeMeters,
            maxAltitudeMeters = maxAltitudeMeters,
            points = points,
            isVisibleOnMap = isVisibleOnMap,
            colorHex = colorHex
        )
    }

    private fun RecordedTrack.toEntity(): TrackEntity {
        val encodedPoints = json.encodeToString(points)
        return TrackEntity(
            id = id,
            name = name,
            description = description,
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis,
            totalDistanceMeters = totalDistanceMeters,
            movingTimeMillis = movingTimeMillis,
            elevationGainMeters = elevationGainMeters,
            elevationLossMeters = elevationLossMeters,
            maxSpeedMetersPerSec = maxSpeedMetersPerSec,
            minAltitudeMeters = minAltitudeMeters,
            maxAltitudeMeters = maxAltitudeMeters,
            pointsJson = encodedPoints,
            isVisibleOnMap = isVisibleOnMap,
            colorHex = colorHex
        )
    }

    private fun WaypointEntity.toDomain(): Waypoint {
        val category = runCatching {
            WaypointCategory.valueOf(categoryName)
        }.getOrDefault(WaypointCategory.CUSTOM)
        return Waypoint(
            id = id,
            name = name,
            description = description,
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitudeMeters,
            category = category,
            createdAtMillis = createdAtMillis
        )
    }

    private fun Waypoint.toEntity(): WaypointEntity =
        WaypointEntity(
            id = id,
            name = name,
            description = description,
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitudeMeters,
            categoryName = category.name,
            createdAtMillis = createdAtMillis
        )
}
