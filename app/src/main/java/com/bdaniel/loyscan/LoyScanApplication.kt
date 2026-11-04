package com.bdaniel.loyscan

import android.app.Application
import com.bdaniel.loyscan.widget.glance.CardWidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber

@HiltAndroidApp
class LoyScanApplication : Application() {

    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Keep the home-screen widget in step with the cards while the app is alive (#11). The updater stays off the
        // database when no widget is placed.
        EntryPointAccessors.fromApplication(this, CardWidgetEntryPoint::class.java)
            .cardWidgetUpdater()
            .start(widgetScope)
    }
}
