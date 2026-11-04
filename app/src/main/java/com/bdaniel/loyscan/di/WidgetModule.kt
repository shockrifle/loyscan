package com.bdaniel.loyscan.di

import com.bdaniel.loyscan.widget.CardWidgetRefresher
import com.bdaniel.loyscan.widget.glance.GlanceCardWidgetRefresher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {

    @Binds
    abstract fun bindCardWidgetRefresher(impl: GlanceCardWidgetRefresher): CardWidgetRefresher
}
