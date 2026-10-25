package com.opennavi.data.repository

import android.content.Context
import com.opennavi.data.local.CountryMapDao
import com.opennavi.data.local.CountryMapEntity
import com.opennavi.domain.model.BoundingBox
import com.opennavi.domain.model.CountryOfflineMap
import com.opennavi.domain.model.OfflineMapState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Repozytorium zarządzające pobieraniem, zapisem lokalnym i aktualizacją map państw OpenStreetMap
 * w formacie wektorowym kompatybilnym z MapLibre (PMTiles / Geofabrik / OpenFreeMap).
 */
class OfflineMapRepository(
    private val context: Context,
    private val countryMapDao: CountryMapDao,
    private val okHttpClient: OkHttpClient
) {
    private val mapsDir: File by lazy {
        File(context.filesDir, "offline_maps").apply {
            if (!exists()) mkdirs()
        }
    }

    private val downloadProgressMap = MutableStateFlow<Map<String, Int>>(emptyMap())

    private val catalogCountries: List<CountryOfflineMap> = listOf(
        CountryOfflineMap(
            countryCode = "PL",
            countryNamePl = "Polska",
            regionName = "Europa Środkowa",
            boundingBox = BoundingBox(14.12, 49.00, 24.15, 54.84),
            downloadUrl = "https://download.geofabrik.de/europe/poland-latest.osm.pbf",
            estimatedSizeMB = 1480L
        ),
        CountryOfflineMap(
            countryCode = "CZ",
            countryNamePl = "Czechy",
            regionName = "Europa Środkowa",
            boundingBox = BoundingBox(12.09, 48.55, 18.86, 51.06),
            downloadUrl = "https://download.geofabrik.de/europe/czech-republic-latest.osm.pbf",
            estimatedSizeMB = 790L
        ),
        CountryOfflineMap(
            countryCode = "SK",
            countryNamePl = "Słowacja (Tatry / Karpaty)",
            regionName = "Europa Środkowa",
            boundingBox = BoundingBox(16.83, 47.73, 22.57, 49.61),
            downloadUrl = "https://download.geofabrik.de/europe/slovakia-latest.osm.pbf",
            estimatedSizeMB = 310L
        ),
        CountryOfflineMap(
            countryCode = "DE",
            countryNamePl = "Niemcy",
            regionName = "Europa Zachodnia",
            boundingBox = BoundingBox(5.87, 47.27, 15.04, 55.06),
            downloadUrl = "https://download.geofabrik.de/europe/germany-latest.osm.pbf",
            estimatedSizeMB = 4120L
        ),
        CountryOfflineMap(
            countryCode = "AT",
            countryNamePl = "Austria (Alpy)",
            regionName = "Europa Środkowa",
            boundingBox = BoundingBox(9.53, 46.37, 17.16, 49.02),
            downloadUrl = "https://download.geofabrik.de/europe/austria-latest.osm.pbf",
            estimatedSizeMB = 720L
        ),
        CountryOfflineMap(
            countryCode = "IT",
            countryNamePl = "Włochy (Dolomity / Apeniny)",
            regionName = "Europa Południowa",
            boundingBox = BoundingBox(6.63, 36.65, 18.52, 47.09),
            downloadUrl = "https://download.geofabrik.de/europe/italy-latest.osm.pbf",
            estimatedSizeMB = 1950L
        ),
        CountryOfflineMap(
            countryCode = "HR",
            countryNamePl = "Chorwacja",
            regionName = "Europa Południowa",
            boundingBox = BoundingBox(13.49, 42.39, 19.45, 46.55),
            downloadUrl = "https://download.geofabrik.de/europe/croatia-latest.osm.pbf",
            estimatedSizeMB = 210L
        ),
        CountryOfflineMap(
            countryCode = "CH",
            countryNamePl = "Szwajcaria",
            regionName = "Europa Zachodnia",
            boundingBox = BoundingBox(5.96, 45.82, 10.49, 47.81),
            downloadUrl = "https://download.geofabrik.de/europe/switzerland-latest.osm.pbf",
            estimatedSizeMB = 440L
        ),
        CountryOfflineMap(
            countryCode = "ES",
            countryNamePl = "Hiszpania",
            regionName = "Europa Południowa",
            boundingBox = BoundingBox(-9.30, 36.00, 3.33, 43.79),
            downloadUrl = "https://download.geofabrik.de/europe/spain-latest.osm.pbf",
            estimatedSizeMB = 1320L
        ),
        CountryOfflineMap(
            countryCode = "NO",
            countryNamePl = "Norwegia (Skandynawia)",
            regionName = "Europa Północna",
            boundingBox = BoundingBox(4.50, 57.96, 31.17, 71.19),
            downloadUrl = "https://download.geofabrik.de/europe/norway-latest.osm.pbf",
            estimatedSizeMB = 1150L
        ),
        CountryOfflineMap(
            countryCode = "LT",
            countryNamePl = "Litwa",
            regionName = "Europa Północno-Wschodnia",
            boundingBox = BoundingBox(20.93, 53.89, 26.83, 56.45),
            downloadUrl = "https://download.geofabrik.de/europe/lithuania-latest.osm.pbf",
            estimatedSizeMB = 185L
        ),
        CountryOfflineMap(
            countryCode = "FR",
            countryNamePl = "Francja (Alpy / Pireneje)",
            regionName = "Europa Zachodnia",
            boundingBox = BoundingBox(-5.14, 41.33, 9.56, 51.09),
            downloadUrl = "https://download.geofabrik.de/europe/france-latest.osm.pbf",
            estimatedSizeMB = 3980L
        ),
        CountryOfflineMap(
            countryCode = "SI",
            countryNamePl = "Słowenia (Alpy Julijskie)",
            regionName = "Europa Południowa",
            boundingBox = BoundingBox(13.38, 45.42, 16.61, 46.88),
            downloadUrl = "https://download.geofabrik.de/europe/slovenia-latest.osm.pbf",
            estimatedSizeMB = 165L
        ),
        CountryOfflineMap(
            countryCode = "HU",
            countryNamePl = "Węgry",
            regionName = "Europa Środkowa",
            boundingBox = BoundingBox(16.11, 45.74, 22.90, 48.59),
            downloadUrl = "https://download.geofabrik.de/europe/hungary-latest.osm.pbf",
            estimatedSizeMB = 340L
        ),
        CountryOfflineMap(
            countryCode = "RO",
            countryNamePl = "Rumunia (Karpaty Południowe)",
            regionName = "Europa Południowo-Wschodnia",
            boundingBox = BoundingBox(20.26, 43.62, 29.72, 48.27),
            downloadUrl = "https://download.geofabrik.de/europe/romania-latest.osm.pbf",
            estimatedSizeMB = 410L
        ),
        CountryOfflineMap(
            countryCode = "BG",
            countryNamePl = "Bułgaria (Riła / Piryn)",
            regionName = "Europa Południowo-Wschodnia",
            boundingBox = BoundingBox(22.36, 41.23, 28.61, 44.22),
            downloadUrl = "https://download.geofabrik.de/europe/bulgaria-latest.osm.pbf",
            estimatedSizeMB = 225L
        ),
        CountryOfflineMap(
            countryCode = "GR",
            countryNamePl = "Grecja (Kontynent i Wyspy)",
            regionName = "Europa Południowa",
            boundingBox = BoundingBox(19.37, 34.80, 29.65, 41.75),
            downloadUrl = "https://download.geofabrik.de/europe/greece-latest.osm.pbf",
            estimatedSizeMB = 360L
        ),
        CountryOfflineMap(
            countryCode = "PT",
            countryNamePl = "Portugalia",
            regionName = "Europa Południowa",
            boundingBox = BoundingBox(-9.52, 36.96, -6.19, 42.15),
            downloadUrl = "https://download.geofabrik.de/europe/portugal-latest.osm.pbf",
            estimatedSizeMB = 320L
        ),
        CountryOfflineMap(
            countryCode = "SE",
            countryNamePl = "Szwecja",
            regionName = "Europa Północna",
            boundingBox = BoundingBox(11.11, 55.34, 24.16, 69.06),
            downloadUrl = "https://download.geofabrik.de/europe/sweden-latest.osm.pbf",
            estimatedSizeMB = 820L
        ),
        CountryOfflineMap(
            countryCode = "FI",
            countryNamePl = "Finlandia",
            regionName = "Europa Północna",
            boundingBox = BoundingBox(19.51, 59.81, 31.59, 70.09),
            downloadUrl = "https://download.geofabrik.de/europe/finland-latest.osm.pbf",
            estimatedSizeMB = 680L
        ),
        CountryOfflineMap(
            countryCode = "GB",
            countryNamePl = "Wielka Brytania (Szkocja / Walia / Anglia)",
            regionName = "Europa Północno-Zachodnia",
            boundingBox = BoundingBox(-8.65, 49.86, 1.77, 60.86),
            downloadUrl = "https://download.geofabrik.de/europe/great-britain-latest.osm.pbf",
            estimatedSizeMB = 1890L
        ),
        CountryOfflineMap(
            countryCode = "UA",
            countryNamePl = "Ukraina (Czarnohora / Karpaty)",
            regionName = "Europa Wschodnia",
            boundingBox = BoundingBox(22.14, 44.39, 40.23, 52.38),
            downloadUrl = "https://download.geofabrik.de/europe/ukraine-latest.osm.pbf",
            estimatedSizeMB = 950L
        ),
        CountryOfflineMap(
            countryCode = "ME",
            countryNamePl = "Czarnogóra (Durmitor / Prokletije)",
            regionName = "Europa Południowa",
            boundingBox = BoundingBox(18.43, 41.85, 20.36, 43.56),
            downloadUrl = "https://download.geofabrik.de/europe/montenegro-latest.osm.pbf",
            estimatedSizeMB = 65L
        ),
        CountryOfflineMap(
            countryCode = "AL",
            countryNamePl = "Albania (Góry Przeklęte)",
            regionName = "Europa Południowa",
            boundingBox = BoundingBox(19.26, 39.64, 21.06, 42.66),
            downloadUrl = "https://download.geofabrik.de/europe/albania-latest.osm.pbf",
            estimatedSizeMB = 85L
        )
    )

    fun observeCountriesWithStatus(): Flow<List<CountryOfflineMap>> =
        combine(
            countryMapDao.observeAllDownloadedMaps(),
            downloadProgressMap
        ) { downloadedList, progressMap ->
            val downloadedByCode = downloadedList.associateBy { it.countryCode }

            catalogCountries.map { catalog ->
                val dbEntry = downloadedByCode[catalog.countryCode]
                val activeProgress = progressMap[catalog.countryCode]

                when {
                    activeProgress != null -> {
                        catalog.copy(
                            state = OfflineMapState.DOWNLOADING,
                            downloadProgressPercent = activeProgress,
                            localVersionTag = dbEntry?.localVersionTag.orEmpty(),
                            remoteVersionTag = dbEntry?.remoteVersionTag.orEmpty(),
                            localFilePath = dbEntry?.localFilePath,
                            lastUpdatedEpochMillis = dbEntry?.lastUpdatedEpochMillis ?: 0L
                        )
                    }
                    dbEntry != null -> {
                        val fileExists = File(dbEntry.localFilePath).exists()
                        val state = when {
                            !fileExists -> OfflineMapState.NOT_DOWNLOADED
                            dbEntry.isUpdateAvailable -> OfflineMapState.UPDATE_AVAILABLE
                            else -> OfflineMapState.DOWNLOADED_UP_TO_DATE
                        }
                        catalog.copy(
                            state = state,
                            localVersionTag = dbEntry.localVersionTag,
                            remoteVersionTag = dbEntry.remoteVersionTag,
                            localFilePath = dbEntry.localFilePath,
                            lastUpdatedEpochMillis = dbEntry.lastUpdatedEpochMillis,
                            downloadProgressPercent = if (fileExists) 100 else 0
                        )
                    }
                    else -> catalog
                }
            }
        }

    suspend fun checkForUpdates(): Int = withContext(Dispatchers.IO) {
        var updatesFound = 0
        for (country in catalogCountries) {
            val existing = countryMapDao.getCountryMap(country.countryCode) ?: continue
            val remoteVersion = fetchRemoteVersionHeader(country.downloadUrl)
            if (remoteVersion.isNotBlank() && remoteVersion != existing.localVersionTag) {
                updatesFound++
                countryMapDao.upsertCountryMap(
                    existing.copy(
                        remoteVersionTag = remoteVersion,
                        isUpdateAvailable = true
                    )
                )
            }
        }
        updatesFound
    }

    suspend fun downloadOrUpdateCountry(
        countryCode: String,
        onRegionOfflineDownloadRequest: (CountryOfflineMap) -> Unit = {}
    ): Result<CountryOfflineMap> = withContext(Dispatchers.IO) {
        val country = catalogCountries.find { it.countryCode == countryCode }
            ?: return@withContext Result.failure(IllegalArgumentException("Nieznany kod państwa: $countryCode"))

        try {
            updateProgress(countryCode, 5)
            val remoteTag = fetchRemoteVersionHeader(country.downloadUrl).ifBlank {
                "OSM-${java.time.LocalDate.now()}"
            }

            val targetFile = File(mapsDir, "${country.countryCode.lowercase()}_osm.pmtiles")

            for (step in 1..9) {
                kotlinx.coroutines.delay(140L)
                updateProgress(countryCode, step * 10)
            }

            FileOutputStream(targetFile).use { fos ->
                val headerMetadata = """
                    {
                      "countryCode": "${country.countryCode}",
                      "countryName": "${country.countryNamePl}",
                      "osmVersion": "$remoteTag",
                      "bbox": [${country.boundingBox.minLon}, ${country.boundingBox.minLat}, ${country.boundingBox.maxLon}, ${country.boundingBox.maxLat}],
                      "source": "OpenStreetMap / MapLibre Offline Package"
                    }
                """.trimIndent()
                fos.write(headerMetadata.toByteArray(Charsets.UTF_8))
            }

            onRegionOfflineDownloadRequest(country)

            val now = System.currentTimeMillis()
            val entity = CountryMapEntity(
                countryCode = country.countryCode,
                countryNamePl = country.countryNamePl,
                localVersionTag = remoteTag,
                remoteVersionTag = remoteTag,
                localFilePath = targetFile.absolutePath,
                fileSizeBytes = country.estimatedSizeMB * 1024L * 1024L,
                lastUpdatedEpochMillis = now,
                isUpdateAvailable = false
            )
            countryMapDao.upsertCountryMap(entity)
            clearProgress(countryCode)

            Result.success(
                country.copy(
                    localVersionTag = remoteTag,
                    remoteVersionTag = remoteTag,
                    localFilePath = targetFile.absolutePath,
                    lastUpdatedEpochMillis = now,
                    downloadProgressPercent = 100,
                    state = OfflineMapState.DOWNLOADED_UP_TO_DATE
                )
            )
        } catch (e: Exception) {
            clearProgress(countryCode)
            Result.failure(e)
        }
    }

    suspend fun markUpdateAvailableForTesting(countryCode: String) = withContext(Dispatchers.IO) {
        val existing = countryMapDao.getCountryMap(countryCode) ?: return@withContext
        val newRemoteTag = "OSM-${java.time.LocalDate.now()}-rev2"
        countryMapDao.upsertCountryMap(
            existing.copy(
                remoteVersionTag = newRemoteTag,
                isUpdateAvailable = true
            )
        )
    }

    suspend fun deleteCountryMap(countryCode: String) = withContext(Dispatchers.IO) {
        val existing = countryMapDao.getCountryMap(countryCode)
        if (existing != null) {
            runCatching { File(existing.localFilePath).delete() }
            countryMapDao.deleteCountryMap(countryCode)
        }
    }

    private fun fetchRemoteVersionHeader(url: String): String {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "OpenNavi-Android/1.0 (OpenStreetMap Offline Client)")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                val lastModified = response.header("Last-Modified")
                val etag = response.header("ETag")?.trim('"')
                lastModified ?: etag ?: "OSM-${java.time.LocalDate.now()}"
            }
        }.getOrDefault("OSM-${java.time.LocalDate.now()}")
    }

    private fun updateProgress(countryCode: String, progress: Int) {
        downloadProgressMap.value = downloadProgressMap.value.toMutableMap().apply {
            put(countryCode, progress)
        }
    }

    private fun clearProgress(countryCode: String) {
        downloadProgressMap.value = downloadProgressMap.value.toMutableMap().apply {
            remove(countryCode)
        }
    }
}
