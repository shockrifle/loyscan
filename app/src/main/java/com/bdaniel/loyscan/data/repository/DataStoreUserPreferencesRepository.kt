package com.bdaniel.loyscan.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bdaniel.loyscan.domain.CardSort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Preferences in one DataStore file ([createDataStore]). A value this version doesn't know reads as the default. */
@Singleton
class DataStoreUserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {

    override val cardSort: Flow<CardSort> = dataStore.data
        .map { preferences -> CardSort.entries.firstOrNull { it.name == preferences[CARD_SORT] } ?: CardSort.NAME }
        .distinctUntilChanged()

    override suspend fun setCardSort(sort: CardSort) {
        dataStore.edit { it[CARD_SORT] = sort.name }
    }

    companion object {
        // Stored by enum name: renaming a CardSort entry resets the users who chose it to the default.
        private val CARD_SORT = stringPreferencesKey("card_sort")

        /** The store as the app opens it: a corrupt file is replaced with empty preferences rather than failing reads. */
        fun createDataStore(scope: CoroutineScope, produceFile: () -> File): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { exception ->
                    Timber.w(exception, "User preferences were corrupt; starting from the defaults")
                    emptyPreferences()
                },
                scope = scope,
                produceFile = produceFile,
            )
    }
}
