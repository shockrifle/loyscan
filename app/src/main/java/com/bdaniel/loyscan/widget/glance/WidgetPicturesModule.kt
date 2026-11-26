package com.bdaniel.loyscan.widget.glance

import android.content.Context
import com.bdaniel.loyscan.data.photos.FileCardPhotos
import com.bdaniel.loyscan.widget.WidgetPictures
import com.bdaniel.loyscan.widget.widgetLogoBoxPx
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The widget's logos (#181), one for the app, so a logo is decoded once however often the widget redraws. Only the
 * widget asks for it, on a real home screen, so it sits with the Glance glue.
 */
@Module
@InstallIn(SingletonComponent::class)
object WidgetPicturesModule {

    @Provides
    @Singleton
    fun provideWidgetPictures(photos: FileCardPhotos, @ApplicationContext context: Context): WidgetPictures =
        WidgetPictures(photos::file, widgetLogoBoxPx(context.resources.displayMetrics.density))
}
