package com.bdaniel.loyscan.widget

/**
 * The two Glance/AppWidget calls the live-update trigger needs ([CardWidgetUpdater]), behind an interface so the
 * updater's logic is testable with a fake. The real implementation (`glance.GlanceCardWidgetRefresher`) only runs on a
 * real home screen, so it is excluded from coverage like the other Glance glue (#11).
 */
interface CardWidgetRefresher {
    /** Whether any home-screen widget is placed, so the updater doesn't observe the database for nothing. */
    suspend fun hasWidget(): Boolean

    /** Redraws every placed widget with the current cards. */
    suspend fun refresh()
}
