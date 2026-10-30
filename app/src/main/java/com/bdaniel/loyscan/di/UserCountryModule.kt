package com.bdaniel.loyscan.di

import com.bdaniel.loyscan.data.device.DeviceUserCountry
import com.bdaniel.loyscan.data.device.UserCountry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class UserCountryModule {

    @Binds
    abstract fun bindUserCountry(impl: DeviceUserCountry): UserCountry
}
