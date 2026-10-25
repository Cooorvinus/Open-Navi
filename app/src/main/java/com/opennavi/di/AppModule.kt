package com.opennavi.di

import android.content.Context
import androidx.room.Room
import com.opennavi.data.local.OpenNaviDatabase
import com.opennavi.data.repository.OfflineMapRepository
import com.opennavi.data.repository.TrackAndWaypointRepository
import com.opennavi.service.LocationAndCompassTracker
import com.opennavi.service.TrackRecordingController
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Lekki kontener zależności aplikacji Open Navi.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: OpenNaviDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            OpenNaviDatabase::class.java,
            "open_navi_locus.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    val locationTracker: LocationAndCompassTracker by lazy {
        LocationAndCompassTracker(appContext)
    }

    val recordingController: TrackRecordingController by lazy {
        TrackRecordingController()
    }

    val offlineMapRepository: OfflineMapRepository by lazy {
        OfflineMapRepository(
            context = appContext,
            countryMapDao = database.countryMapDao(),
            okHttpClient = okHttpClient
        )
    }

    val trackAndWaypointRepository: TrackAndWaypointRepository by lazy {
        TrackAndWaypointRepository(
            trackDao = database.trackDao(),
            waypointDao = database.waypointDao(),
            okHttpClient = okHttpClient
        )
    }
}
