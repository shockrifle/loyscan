package com.bdaniel.loyscan.di

import android.content.Context
import com.bdaniel.loyscan.data.photos.CardPhotos
import com.bdaniel.loyscan.data.photos.FileCardPhotos
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CardPhotosModule {

    /** App-private, so no other app can read them, and no storage permission is needed (#10). */
    @Provides
    @Singleton
    fun provideFileCardPhotos(@ApplicationContext context: Context): FileCardPhotos =
        FileCardPhotos(File(context.filesDir, PHOTO_DIRECTORY), context.contentResolver)

    @Provides
    fun provideCardPhotos(photos: FileCardPhotos): CardPhotos = photos

    const val PHOTO_DIRECTORY = "card_photos"
}
