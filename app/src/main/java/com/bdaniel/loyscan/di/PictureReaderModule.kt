package com.bdaniel.loyscan.di

import com.bdaniel.loyscan.ui.scanner.PictureReader
import com.bdaniel.loyscan.ui.scanner.ZxingCppPictureReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PictureReaderModule {

    @Binds
    abstract fun bindPictureReader(impl: ZxingCppPictureReader): PictureReader
}
