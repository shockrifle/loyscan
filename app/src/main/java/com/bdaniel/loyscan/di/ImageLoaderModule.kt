package com.bdaniel.loyscan.di

import android.content.Context
import coil.EventListener
import coil.ImageLoader
import coil.decode.SvgDecoder
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .eventListener(object : EventListener {
                override fun onError(request: coil.request.ImageRequest, result: coil.request.ErrorResult) {
                    Timber.w(result.throwable, "Image loading failed for: ${request.data}")
                }
            })
            .build()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ImageLoaderEntryPoint {
    fun getImageLoader(): ImageLoader
}
