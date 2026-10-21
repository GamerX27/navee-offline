package com.xlabsx27.naveeoffline

import android.app.Application
import org.mapsforge.core.util.Parameters
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

class NaveeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AndroidGraphicFactory.createInstance(this)
        // Mapsforge defaults to one render thread per core + 1. With a country-sized map each thread
        // can hold a lot of geometry, and 9 at once ran a phone out of heap. Two is plenty.
        Parameters.NUMBER_OF_THREADS = 2
        container = AppContainer(this)
    }
}
