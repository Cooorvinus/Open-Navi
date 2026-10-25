package com.opennavi.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "country_maps")
data class CountryMapEntity(
    @PrimaryKey val countryCode: String,
    val countryNamePl: String,
    val localVersionTag: String,
    val remoteVersionTag: String,
    val localFilePath: String,
    val fileSizeBytes: Long,
    val lastUpdatedEpochMillis: Long,
    val isUpdateAvailable: Boolean = false
)

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val totalDistanceMeters: Double,
    val movingTimeMillis: Long,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val maxSpeedMetersPerSec: Float,
    val minAltitudeMeters: Double,
    val maxAltitudeMeters: Double,
    val pointsJson: String,
    val isVisibleOnMap: Boolean = true,
    val colorHex: String = "#E53935"
)

@Entity(tableName = "waypoints")
data class WaypointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val folderName: String = "Ulubione",
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val categoryName: String,
    val colorHex: String = "#D32F2F",
    val phone: String = "",
    val url: String = "",
    val createdAtMillis: Long
)

@Dao
interface CountryMapDao {
    @Query("SELECT * FROM country_maps ORDER BY countryNamePl ASC")
    fun observeAllDownloadedMaps(): Flow<List<CountryMapEntity>>

    @Query("SELECT * FROM country_maps WHERE countryCode = :countryCode LIMIT 1")
    suspend fun getCountryMap(countryCode: String): CountryMapEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCountryMap(entity: CountryMapEntity)

    @Query("DELETE FROM country_maps WHERE countryCode = :countryCode")
    suspend fun deleteCountryMap(countryCode: String)
}

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY startTimeMillis DESC")
    fun observeAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: Long): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(entity: TrackEntity): Long

    @Update
    suspend fun updateTrack(entity: TrackEntity)

    @Delete
    suspend fun deleteTrack(entity: TrackEntity)
}

@Dao
interface WaypointDao {
    @Query("SELECT * FROM waypoints ORDER BY createdAtMillis DESC")
    fun observeAllWaypoints(): Flow<List<WaypointEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoint(entity: WaypointEntity): Long

    @Delete
    suspend fun deleteWaypoint(entity: WaypointEntity)
}

@Database(
    entities = [
        CountryMapEntity::class,
        TrackEntity::class,
        WaypointEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class OpenNaviDatabase : RoomDatabase() {
    abstract fun countryMapDao(): CountryMapDao
    abstract fun trackDao(): TrackDao
    abstract fun waypointDao(): WaypointDao
}
