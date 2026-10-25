package com.opennavi

import android.app.Application
import com.opennavi.di.AppContainer
import org.maplibre.android.MapLibre

class OpenNaviApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Inicjalizacja silnika MapLibre Native (100% Open Source, bez kluczy płatnych)
        MapLibre.getInstance(this)
    }
}
