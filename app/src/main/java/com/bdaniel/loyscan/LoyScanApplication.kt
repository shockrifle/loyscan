package com.bdaniel.loyscan

import android.app.Application
import com.bdaniel.loyscan.widget.glance.CardWidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class LoyScanApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Keep the home-screen widget in step with the cards while the app is alive (#11). It observes nothing when no
        // widget is placed; a widget added later is picked up by the receiver.
        EntryPointAccessors.fromApplication(this, CardWidgetEntryPoint::class.java)
            .cardWidgetUpdater()
            .ensureObserving()
    }
}
