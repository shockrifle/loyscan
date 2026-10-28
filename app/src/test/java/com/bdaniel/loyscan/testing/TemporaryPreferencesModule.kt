package com.bdaniel.loyscan.testing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.bdaniel.loyscan.data.repository.DataStoreUserPreferencesRepository
import com.bdaniel.loyscan.di.PreferencesModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.UUID
import javax.inject.Singleton

/**
 * In `@HiltAndroidTest`s, each test's app gets its own new preferences file: the app's one would be shared by every
 * test in the process, and DataStore throws when a second store opens a file another still has open.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PreferencesModule::class])
object TemporaryPreferencesModule {

    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        DataStoreUserPreferencesRepository.createDataStore(CoroutineScope(Dispatchers.IO + SupervisorJob())) {
            File(context.cacheDir, "user_preferences-${UUID.randomUUID()}.preferences_pb")
        }
}
