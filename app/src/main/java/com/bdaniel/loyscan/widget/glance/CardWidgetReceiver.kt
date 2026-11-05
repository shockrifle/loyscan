package com.bdaniel.loyscan.widget.glance

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dagger.hilt.android.EntryPointAccessors

/** The manifest's entry point for the home-screen widget (#11): hands the launcher the [CardWidget]. */
class CardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CardWidget()

    /**
     * Fires when a widget is added and when the host asks for an update (including after the process was recreated to
     * show the widget). Starts the live-update observer so a widget placed while the app runs — or shown after a
     * restart — follows the cards from then on (#11). [CardWidgetUpdater.ensureObserving] is idempotent.
     */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        EntryPointAccessors.fromApplication(context, CardWidgetEntryPoint::class.java)
            .cardWidgetUpdater()
            .ensureObserving()
    }
}
