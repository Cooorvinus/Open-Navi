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
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.opennavi.domain.model.GnssSatelliteDetail
import com.opennavi.domain.model.GpsStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Menedżer fuzji sensorów (GNSS / GPS + Kompas / Wektor obrotu) w stylu Locus Map.
 * Natywnie obsługuje API Androida 15–17.
 */
class LocationAndCompassTracker(
    private val context: Context
) : LocationListener, SensorEventListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _gpsStatus = MutableStateFlow(GpsStatus())
    val gpsStatus: StateFlow<GpsStatus> = _gpsStatus.asStateFlow()

    private var isTracking = false
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

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

        _gpsStatus.update {
            it.copy(
                hasPermission = hasFine || hasCoarse,
                isGpsEnabled = gpsEnabled
            )
        }

        if (!(hasFine || hasCoarse) || isTracking) return
        isTracking = true

        runCatching {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1.0f,
                    this,
                    Looper.getMainLooper()
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2500L,
                    5.0f,
                    this,
                    Looper.getMainLooper()
                )
            }
            val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (lastGps != null) {
                // Dla zbuforowanej starej lokalizacji przy starcie aplikacji zerujemy prędkość
                lastGps.removeSpeed()
                onLocationChanged(lastGps)
            }
            if (hasFine) {
                locationManager.registerGnssStatusCallback(gnssStatusCallback, mainHandler)
            }
        }

        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { rotationSensor ->
            sensorManager.registerListener(
                this,
                rotationSensor,
                SensorManager.SENSOR_DELAY_UI
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
        val isFromGpsProvider = location.provider == LocationManager.GPS_PROVIDER
        val currentAcc = if (location.hasAccuracy()) location.accuracy else 999f

        _gpsStatus.update { current ->
            // Filtr prędkości w stylu Locus Map (eliminacja fałszywych skoków prędkości wewnątrz budynków):
            // 1. Prędkość pochodzi wyłącznie z odbiornika satelitarnego GPS (ignorujemy skoki masztów LTE/Wi-Fi).
            // 2. Wymagamy aktywnego Fixu z satelitów (satellitesUsedInFix >= 3) oraz dokładności <= 25 m.
            // 3. Odcinamy szum spoczynkowy poniżej 0.5 m/s (~1.8 km/h).
            val hasReliableGpsSpeed = isFromGpsProvider &&
                location.hasSpeed() &&
                currentAcc <= 25f &&
                (current.satellitesUsedInFix >= 3 || currentAcc <= 12f)

            val filteredSpeed = if (hasReliableGpsSpeed && location.speed >= 0.5f) {
                location.speed
            } else {
                0f
            }

            current.copy(
                latitude = location.latitude,
                longitude = location.longitude,
                altitudeMeters = if (location.hasAltitude()) location.altitude else current.altitudeMeters,
                speedMetersPerSec = filteredSpeed,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else current.accuracyMeters,
                bearingDegrees = if (hasReliableGpsSpeed && location.hasBearing()) location.bearing else current.bearingDegrees,
                lastFixTimestamp = location.time
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val azimuthRad = orientationAngles[0]
            val azimuthDeg = ((Math.toDegrees(azimuthRad.toDouble()) + 360.0) % 360.0).toFloat()
            _gpsStatus.update { current ->
                current.copy(compassAzimuthDegrees = azimuthDeg)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
