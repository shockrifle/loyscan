package com.bdaniel.loyscan.testing

import com.bdaniel.loyscan.di.PictureReaderModule
import com.bdaniel.loyscan.ui.scanner.PictureReader
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** In `@HiltAndroidTest`s, one [FakePictureReader] reads every picture. Inject it to set what it finds. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PictureReaderModule::class])
object FakePictureReaderModule {

    @Provides
    @Singleton
    fun provideFakePictureReader() = FakePictureReader()

    @Provides
    fun providePictureReader(fake: FakePictureReader): PictureReader = fake
}
