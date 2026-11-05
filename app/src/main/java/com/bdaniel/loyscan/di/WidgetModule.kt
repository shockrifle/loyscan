package com.bdaniel.loyscan.di

import com.bdaniel.loyscan.widget.CardWidgetRefresher
import com.bdaniel.loyscan.widget.glance.GlanceCardWidgetRefresher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber
import javax.inject.Qualifier
import javax.inject.Singleton

/** The app-wide scope the home-screen widget updater observes on (#11). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WidgetScope

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {

    @Binds
    abstract fun bindCardWidgetRefresher(impl: GlanceCardWidgetRefresher): CardWidgetRefresher

    companion object {
        /**
         * A never-cancelled app scope for the widget updater. `SupervisorJob` and the handler keep a failure in the
         * optional widget from ever bringing the app down (the updater also handles its own errors).
         */
        @Provides
        @Singleton
        @WidgetScope
        fun provideWidgetScope(): CoroutineScope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default +
                CoroutineExceptionHandler { _, e -> Timber.e(e, "Card widget updater crashed") }
        )
    }
}
