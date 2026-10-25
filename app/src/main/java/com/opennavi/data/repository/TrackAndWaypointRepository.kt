package com.opennavi.data.repository

import com.opennavi.data.local.TrackDao
import com.opennavi.data.local.TrackEntity
import com.opennavi.data.local.WaypointDao
import com.opennavi.data.local.WaypointEntity
import com.opennavi.domain.model.RecordedTrack
import com.opennavi.domain.model.RouteResult
import com.opennavi.domain.model.RoutingProfile
import com.opennavi.domain.model.TrackPoint
import com.opennavi.domain.model.Waypoint
import com.opennavi.domain.model.WaypointCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class TrackAndWaypointRepository(
    private val trackDao: TrackDao,
    private val waypointDao: WaypointDao,
    private val okHttpClient: OkHttpClient
) {
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
                val root = JSONObject(body)
                val routes = root.optJSONArray("routes")
                if (routes == null || routes.length() == 0) {
                    error("Nie znaleziono trasy dla wybranego odcinka")
                }

                val firstRoute = routes.getJSONObject(0)
                val distanceMeters = firstRoute.optDouble("distance", 0.0)
                val durationSeconds = firstRoute.optDouble("duration", 0.0)

                val coordinatesArray = firstRoute
                    .optJSONObject("geometry")
                    ?.optJSONArray("coordinates")
                    ?: error("Brak geometrii trasy")

                val geometryPoints = mutableListOf<Pair<Double, Double>>()
                for (i in 0 until coordinatesArray.length()) {
                    val pair = coordinatesArray.getJSONArray(i)
                    val lon = pair.getDouble(0)
                    val lat = pair.getDouble(1)
                    geometryPoints.add(lat to lon)
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
            val arr = JSONArray(pointsJson)
            val list = mutableListOf<TrackPoint>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    TrackPoint(
                        latitude = obj.getDouble("lat"),
                        longitude = obj.getDouble("lon"),
                        altitudeMeters = obj.optDouble("alt", 0.0),
                        timestampMillis = obj.optLong("time", 0L),
                        speedMetersPerSec = obj.optDouble("spd", 0.0).toFloat(),
                        accuracyMeters = obj.optDouble("acc", 0.0).toFloat()
                    )
                )
            }
            list
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
        val arr = JSONArray()
        for (pt in points) {
            val obj = JSONObject().apply {
                put("lat", pt.latitude)
                put("lon", pt.longitude)
                put("alt", pt.altitudeMeters)
                put("time", pt.timestampMillis)
                put("spd", pt.speedMetersPerSec.toDouble())
                put("acc", pt.accuracyMeters.toDouble())
            }
            arr.put(obj)
        }
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
            pointsJson = arr.toString(),
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
