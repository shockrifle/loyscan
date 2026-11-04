package com.bdaniel.loyscan.widget.glance

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.bdaniel.loyscan.widget.CardWidgetRefresher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real [CardWidgetRefresher]: the two Glance/AppWidget calls the live-update trigger needs. It only runs on a real
 * home screen, so it lives in `widget.glance` and is excluded from coverage like the rest of the Glance glue (#11).
 */
@Singleton
class GlanceCardWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) : CardWidgetRefresher {
    override suspend fun hasWidget(): Boolean =
        GlanceAppWidgetManager(context).getGlanceIds(CardWidget::class.java).isNotEmpty()

    override suspend fun refresh() = CardWidget().updateAll(context)
}
