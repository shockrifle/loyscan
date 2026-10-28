package com.bdaniel.loyscan.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.bdaniel.loyscan.data.repository.DataStoreUserPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    /** One store per file per process: DataStore throws if a second one opens the same file. */
    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        userPreferencesDataStore(context, CoroutineScope(Dispatchers.IO + SupervisorJob()))

    fun userPreferencesDataStore(context: Context, scope: CoroutineScope): DataStore<Preferences> =
        DataStoreUserPreferencesRepository.createDataStore(scope) { context.preferencesDataStoreFile("user_preferences") }
}
