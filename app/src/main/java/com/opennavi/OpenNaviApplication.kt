package com.opennavi

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre

@HiltAndroidApp
class OpenNaviApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicjalizacja silnika MapLibre Native (100% Open Source, bez kluczy płatnych)
        MapLibre.getInstance(this)
    }
}
