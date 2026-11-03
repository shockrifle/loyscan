package com.bdaniel.loyscan.di

import android.content.Context
import androidx.core.content.FileProvider
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

    /**
     * App-private, so no other app can read them, and no storage permission is needed (#10). The camera app gets a
     * capture's file through the app's FileProvider (`res/xml/card_photo_paths.xml`).
     */
    @Provides
    @Singleton
    fun provideFileCardPhotos(@ApplicationContext context: Context): FileCardPhotos =
        FileCardPhotos(File(context.filesDir, PHOTO_DIRECTORY), context.contentResolver) { file ->
            FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
        }

    @Provides
    fun provideCardPhotos(photos: FileCardPhotos): CardPhotos = photos

    const val PHOTO_DIRECTORY = "card_photos"
}
