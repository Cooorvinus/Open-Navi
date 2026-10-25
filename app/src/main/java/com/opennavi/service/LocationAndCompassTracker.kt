package com.opennavi.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.altitude.AltitudeConverter
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.opennavi.domain.model.GnssSatelliteDetail
import com.opennavi.domain.model.GpsStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

/**
 * Menedżer fuzji sensorów (Fused + GNSS/GPS + Barometr + Kompas + konwersja geoidy EGM2008)
 * w stylu Locus Map Pro dla Androida 15–17 (API 35–37).
 */
class LocationAndCompassTracker(
    private val context: Context
) : LocationListener, SensorEventListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val altitudeConverter = AltitudeConverter()

    private val _gpsStatus = MutableStateFlow(GpsStatus())
    val gpsStatus: StateFlow<GpsStatus> = _gpsStatus.asStateFlow()

    private var isTracking = false
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Ostatnia dokładna wysokość n.p.m. (MSL) do automatycznej kalibracji barometru (jak w Locus Map)
    private var lastCalibratedMslAltitude: Double? = null
    private var lastBarometerRawAltitude: Double? = null

    private val gnssStatusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val total = status.satelliteCount
            var used = 0
            val details = ArrayList<GnssSatelliteDetail>(total)
            for (i in 0 until total) {
                val isUsed = status.usedInFix(i)
                if (isUsed) used++
                val constellationName = when (status.getConstellationType(i)) {
                    GnssStatus.CONSTELLATION_GPS -> "GPS (USA)"
                    GnssStatus.CONSTELLATION_GLONASS -> "GLONASS (RU)"
                    GnssStatus.CONSTELLATION_GALILEO -> "Galileo (UE)"
                    GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou (CN)"
                    GnssStatus.CONSTELLATION_QZSS -> "QZSS (JP)"
                    GnssStatus.CONSTELLATION_SBAS -> "SBAS"
                    GnssStatus.CONSTELLATION_IRNSS -> "NavIC (IN)"
                    else -> "GNSS"
                }
                details.add(
                    GnssSatelliteDetail(
                        svid = status.getSvid(i),
                        constellationName = constellationName,
                        cn0DbHz = status.getCn0DbHz(i),
                        usedInFix = isUsed,
                        elevationDegrees = status.getElevationDegrees(i),
                        azimuthDegrees = status.getAzimuthDegrees(i)
                    )
                )
            }
            val sortedDetails = details.sortedWith(
                compareByDescending<GnssSatelliteDetail> { it.usedInFix }
                    .thenByDescending { it.cn0DbHz }
            )
            _gpsStatus.update { current ->
                current.copy(
                    satellitesVisible = total,
                    satellitesUsedInFix = used,
                    satelliteDetails = sortedDetails
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val gpsEnabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)

        val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

        _gpsStatus.update {
            it.copy(
                hasPermission = hasFine || hasCoarse,
                isGpsEnabled = gpsEnabled,
                hasBarometer = pressureSensor != null
            )
        }

        if (!(hasFine || hasCoarse) || isTracking) return
        isTracking = true

        runCatching {
            // 1. FUSED_PROVIDER (Android 12-17) – najlepsza fuzja GNSS + Wi-Fi + czujników inercyjnych
            if (locationManager.allProviders.contains(LocationManager.FUSED_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.FUSED_PROVIDER,
                    1000L,
                    0.5f,
                    this,
                    Looper.getMainLooper()
                )
            }
            // 2. Sprzętowy GPS_PROVIDER
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0.5f,
                    this,
                    Looper.getMainLooper()
                )
            }
            // 3. Sieciowy NETWORK_PROVIDER (jako zapas w głębokich pomieszczeniach)
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2500L,
                    2.0f,
                    this,
                    Looper.getMainLooper()
                )
            }

            // Wybór najświeższej i najdokładniejszej zapisanej pozycji na starcie
            val candidates = listOfNotNull(
                runCatching { locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER) }.getOrNull(),
                runCatching { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull(),
                runCatching { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
            )
            val bestInitial = candidates.maxByOrNull { loc ->
                loc.time - (loc.accuracy * 1000L).toLong()
            }
            if (bestInitial != null) {
                bestInitial.removeSpeed()
                onLocationChanged(bestInitial)
            }

            if (hasFine) {
                locationManager.registerGnssStatusCallback(gnssStatusCallback, mainHandler)
            }
        }

        // Rejestracja kompasu (Rotation Vector)
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { rotationSensor ->
            sensorManager.registerListener(
                this,
                rotationSensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }

        // Rejestracja wysokościomierza barometrycznego (jeśli urządzenie posiada barometr)
        pressureSensor?.let { baro ->
            sensorManager.registerListener(
                this,
                baro,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    fun stopTracking() {
        if (!isTracking) return
        isTracking = false
        runCatching {
            locationManager.removeUpdates(this)
            locationManager.unregisterGnssStatusCallback(gnssStatusCallback)
            sensorManager.unregisterListener(this)
        }
    }

    override fun onLocationChanged(location: Location) {
        val currentState = _gpsStatus.value
        val newAcc = if (location.hasAccuracy()) location.accuracy else 999f
        val ageDeltaMillis = location.time - currentState.lastFixTimestamp

        // Ochrona przed nadpisywaniem dokładnych współrzędnych GPS przez niedokładny zrzut z masztu komórkowego:
        // Jeśli obecna pozycja jest świeża (< 15 s) i znacznie dokładniejsza (np. ±8 m vs ±150 m),
        // odrzucamy mniej dokładny skok współrzędnych.
        if (currentState.lastFixTimestamp > 0L &&
            ageDeltaMillis < 15_000L &&
            currentState.accuracyMeters in 1f..30f &&
            newAcc > currentState.accuracyMeters * 2.5f
        ) {
            return
        }

        // Obliczanie prawdziwej wysokości nad poziomem morza (MSL – Mean Sea Level) zamiast surowej elipsoidy WGS84.
        // W Polsce elipsoida WGS84 zawyża wysokość o ok. +39..+42 metry (np. w Krakowie pokazuje 245 m zamiast ~204 m n.p.m.).
        val mslAltitudeMeters: Double? = if (location.hasAltitude()) {
            runCatching {
                altitudeConverter.addMslAltitudeToLocation(context, location)
                if (location.hasMslAltitude()) {
                    location.mslAltitudeMeters
                } else {
                    location.altitude - estimateGeoidUndulationPoland(location.latitude, location.longitude)
                }
            }.getOrElse {
                location.altitude - estimateGeoidUndulationPoland(location.latitude, location.longitude)
            }
        } else {
            null
        }

        if (mslAltitudeMeters != null && newAcc <= 45f) {
            lastCalibratedMslAltitude = mslAltitudeMeters
        }

        val isFromGpsProvider = location.provider == LocationManager.GPS_PROVIDER
        val hasReliableGpsSpeed = isFromGpsProvider &&
            location.hasSpeed() &&
            newAcc <= 25f &&
            (currentState.satellitesUsedInFix >= 3 || newAcc <= 12f)

        val filteredSpeed = if (hasReliableGpsSpeed && location.speed >= 0.5f) {
            location.speed
        } else {
            0f
        }

        val providerLabel = when (location.provider) {
            LocationManager.GPS_PROVIDER -> "GPS"
            LocationManager.FUSED_PROVIDER -> "Fused GNSS"
            LocationManager.NETWORK_PROVIDER -> "Sieć Wi-Fi/LTE"
            else -> location.provider.orEmpty()
        }

        _gpsStatus.update { current ->
            val resolvedAltitude = when {
                mslAltitudeMeters != null -> {
                    // Wygładzenie wysokości, aby wyeliminować drobne wahania o ±1-2 m
                    if (current.altitudeMeters == 0.0 || abs(mslAltitudeMeters - current.altitudeMeters) > 8.0) {
                        mslAltitudeMeters
                    } else {
                        current.altitudeMeters * 0.7 + mslAltitudeMeters * 0.3
                    }
                }
                else -> current.altitudeMeters
            }

            current.copy(
                latitude = location.latitude,
                longitude = location.longitude,
                altitudeMeters = resolvedAltitude,
                speedMetersPerSec = filteredSpeed,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else current.accuracyMeters,
                bearingDegrees = if (hasReliableGpsSpeed && location.hasBearing()) location.bearing else current.bearingDegrees,
                locationProviderName = providerLabel,
                lastFixTimestamp = location.time
            )
        }
    }

    /**
     * Przybliżona separacja geoidy EGM2008 względem elipsoidy WGS84 dla Europy Środkowej (Polska: ~39.5 - 41.5 m),
     * używana jako fallback, gdy systemowy AltitudeConverter nie zwróci wartości MSL.
     */
    private fun estimateGeoidUndulationPoland(lat: Double, lon: Double): Double {
        return if (lat in 48.0..56.0 && lon in 13.0..25.0) {
            40.5
        } else {
            38.0
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val azimuthRad = orientationAngles[0]
                val azimuthDeg = ((Math.toDegrees(azimuthRad.toDouble()) + 360.0) % 360.0).toFloat()
                _gpsStatus.update { current ->
                    current.copy(compassAzimuthDegrees = azimuthDeg)
                }
            }

            Sensor.TYPE_PRESSURE -> {
                val pressureHpa = event.values[0]
                if (pressureHpa in 800f..1100f) {
                    val rawBaroAlt = SensorManager.getAltitude(
                        SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                        pressureHpa
                    ).toDouble()

                    val prevRaw = lastBarometerRawAltitude
                    lastBarometerRawAltitude = rawBaroAlt

                    val calibratedBase = lastCalibratedMslAltitude
                    if (calibratedBase != null && prevRaw != null) {
                        val deltaH = rawBaroAlt - prevRaw
                        if (abs(deltaH) < 5.0) {
                            val newCalibrated = calibratedBase + deltaH
                            lastCalibratedMslAltitude = newCalibrated
                            _gpsStatus.update { current ->
                                current.copy(altitudeMeters = current.altitudeMeters * 0.8 + newCalibrated * 0.2)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
