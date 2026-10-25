package com.opennavi.data.gpx

import com.opennavi.domain.model.RecordedTrack
import com.opennavi.domain.model.TrackPoint
import com.opennavi.domain.model.Waypoint
import com.opennavi.domain.model.WaypointCategory
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Parser i eksporter standardu GPX 1.1 kompatybilny z Locus Map, Garmin oraz OsmAnd.
 */
object GpxSerializer {

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun exportTrackToGpx(
        track: RecordedTrack,
        waypoints: List<Waypoint> = emptyList(),
        outputStream: OutputStream
    ) {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine(
            """<gpx version="1.1" creator="Open Navi Android (Locus-compatible)" """ +
                """xmlns="http://www.topografix.com/GPX/1/1">"""
        )
        sb.appendLine("  <metadata>")
        sb.appendLine("    <name>${escapeXml(track.name)}</name>")
        if (track.description.isNotBlank()) {
            sb.appendLine("    <desc>${escapeXml(track.description)}</desc>")
        }
        sb.appendLine("    <time>${isoDateFormat.format(Date(track.startTimeMillis))}</time>")
        sb.appendLine("  </metadata>")

        // Zapis punktów Waypoint
        for (wpt in waypoints) {
            sb.appendLine("""  <wpt lat="${wpt.latitude}" lon="${wpt.longitude}">""")
            sb.appendLine("    <ele>${wpt.altitudeMeters}</ele>")
            sb.appendLine("    <name>${escapeXml(wpt.name)}</name>")
            if (wpt.description.isNotBlank()) {
                sb.appendLine("    <desc>${escapeXml(wpt.description)}</desc>")
            }
            sb.appendLine("    <type>${wpt.category.name}</type>")
            sb.appendLine("  </wpt>")
        }

        // Zapis śladu (Track)
        sb.appendLine("  <trk>")
        sb.appendLine("    <name>${escapeXml(track.name)}</name>")
        sb.appendLine("    <trkseg>")
        for (pt in track.points) {
            sb.appendLine("""      <trkpt lat="${pt.latitude}" lon="${pt.longitude}">""")
            sb.appendLine("        <ele>${pt.altitudeMeters}</ele>")
            sb.appendLine("        <time>${isoDateFormat.format(Date(pt.timestampMillis))}</time>")
            sb.appendLine("      </trkpt>")
        }
        sb.appendLine("    </trkseg>")
        sb.appendLine("  </trk>")
        sb.appendLine("</gpx>")

        outputStream.writer(Charsets.UTF_8).use { writer ->
            writer.write(sb.toString())
        }
    }

    data class ImportedGpxData(
        val track: RecordedTrack?,
        val waypoints: List<Waypoint>
    )

    fun importFromGpx(inputStream: InputStream, defaultTrackName: String = "Importowany ślad GPX"): ImportedGpxData {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var trackName = defaultTrackName
        val points = mutableListOf<TrackPoint>()
        val waypoints = mutableListOf<Waypoint>()

        var inTrk = false
        var inTrkPt = false
        var inWpt = false

        var currentLat = 0.0
        var currentLon = 0.0
        var currentEle = 0.0
        var currentTime = System.currentTimeMillis()
        var currentName = ""
        var currentDesc = ""
        var currentType = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase(Locale.ROOT)) {
                        "trk" -> inTrk = true
                        "trkpt" -> {
                            inTrkPt = true
                            currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                            currentEle = 0.0
                            currentTime = System.currentTimeMillis()
                        }
                        "wpt" -> {
                            inWpt = true
                            currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                            currentEle = 0.0
                            currentName = "Punkt GPX"
                            currentDesc = ""
                            currentType = ""
                        }
                        "ele" -> {
                            val text = parser.nextText().trim()
                            currentEle = text.toDoubleOrNull() ?: 0.0
                        }
                        "time" -> {
                            val text = parser.nextText().trim()
                            currentTime = runCatching {
                                isoDateFormat.parse(text)?.time ?: System.currentTimeMillis()
                            }.getOrDefault(System.currentTimeMillis())
                        }
                        "name" -> {
                            val text = parser.nextText().trim()
                            if (inWpt) {
                                currentName = text
                            } else if (inTrk && !inTrkPt && text.isNotBlank()) {
                                trackName = text
                            }
                        }
                        "desc" -> {
                            val text = parser.nextText().trim()
                            if (inWpt) currentDesc = text
                        }
                        "type" -> {
                            val text = parser.nextText().trim()
                            if (inWpt) currentType = text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name.lowercase(Locale.ROOT)) {
                        "trk" -> inTrk = false
                        "trkpt" -> {
                            inTrkPt = false
                            points.add(
                                TrackPoint(
                                    latitude = currentLat,
                                    longitude = currentLon,
                                    altitudeMeters = currentEle,
                                    timestampMillis = currentTime
                                )
                            )
                        }
                        "wpt" -> {
                            inWpt = false
                            val category = runCatching {
                                WaypointCategory.valueOf(currentType.uppercase(Locale.ROOT))
                            }.getOrDefault(WaypointCategory.CUSTOM)
                            waypoints.add(
                                Waypoint(
                                    name = currentName,
                                    description = currentDesc,
                                    latitude = currentLat,
                                    longitude = currentLon,
                                    altitudeMeters = currentEle,
                                    category = category
                                )
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        val importedTrack = if (points.isNotEmpty()) {
            buildTrackStatistics(trackName, points)
        } else null

        return ImportedGpxData(track = importedTrack, waypoints = waypoints)
    }

    fun buildTrackStatistics(name: String, points: List<TrackPoint>): RecordedTrack {
        if (points.isEmpty()) {
            val now = System.currentTimeMillis()
            return RecordedTrack(
                name = name,
                startTimeMillis = now,
                endTimeMillis = now,
                totalDistanceMeters = 0.0,
                movingTimeMillis = 0L,
                elevationGainMeters = 0.0,
                elevationLossMeters = 0.0,
                maxSpeedMetersPerSec = 0f,
                minAltitudeMeters = 0.0,
                maxAltitudeMeters = 0.0,
                points = emptyList()
            )
        }

        var totalDist = 0.0
        var gain = 0.0
        var loss = 0.0
        var maxSpeed = 0f
        var minAlt = points.first().altitudeMeters
        var maxAlt = points.first().altitudeMeters
        var movingTime = 0L

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val dist = haversineDistanceMeters(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
            totalDist += dist

            val altDiff = curr.altitudeMeters - prev.altitudeMeters
            if (altDiff > 1.5) gain += altDiff
            if (altDiff < -1.5) loss += -altDiff

            minAlt = min(minAlt, curr.altitudeMeters)
            maxAlt = max(maxAlt, curr.altitudeMeters)

            val dtMillis = (curr.timestampMillis - prev.timestampMillis).coerceAtLeast(0L)
            if (dist > 0.8 && dtMillis in 1..60_000) {
                movingTime += dtMillis
                val segSpeed = (dist / (dtMillis / 1000.0)).toFloat()
                maxSpeed = max(maxSpeed, max(curr.speedMetersPerSec, segSpeed))
            }
        }

        val start = points.first().timestampMillis
        val end = points.last().timestampMillis
        if (movingTime == 0L && end > start) {
            movingTime = end - start
        }

        return RecordedTrack(
            name = name,
            startTimeMillis = start,
            endTimeMillis = end,
            totalDistanceMeters = totalDist,
            movingTimeMillis = movingTime,
            elevationGainMeters = gain,
            elevationLossMeters = loss,
            maxSpeedMetersPerSec = maxSpeed,
            minAltitudeMeters = minAlt,
            maxAltitudeMeters = maxAlt,
            points = points
        )
    }

    fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Promień Ziemi w metrach
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun escapeXml(input: String): String =
        input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
