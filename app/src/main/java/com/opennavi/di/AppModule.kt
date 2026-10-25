package com.opennavi.di

import android.content.Context
import androidx.room.Room
import com.opennavi.data.local.CountryMapDao
import com.opennavi.data.local.OpenNaviDatabase
import com.opennavi.data.local.TrackDao
import com.opennavi.data.local.WaypointDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OpenNaviDatabase {
        return Room.databaseBuilder(
            context,
            OpenNaviDatabase::class.java,
            "open_navi_locus.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideCountryMapDao(db: OpenNaviDatabase): CountryMapDao = db.countryMapDao()

    @Provides
    fun provideTrackDao(db: OpenNaviDatabase): TrackDao = db.trackDao()

    @Provides
    fun provideWaypointDao(db: OpenNaviDatabase): WaypointDao = db.waypointDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
