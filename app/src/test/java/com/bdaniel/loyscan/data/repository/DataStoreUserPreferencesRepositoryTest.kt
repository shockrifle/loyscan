package com.bdaniel.loyscan.data.repository

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bdaniel.loyscan.domain.CardSort
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The real DataStore on a file, as the app has it. Plain JVM: DataStore's preferences store needs no Android.
 * Each test writes a file at most once: on a Windows host, DataStore can't replace an existing file.
 */
class DataStoreUserPreferencesRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file: File by lazy { File(folder.root, "user_preferences.preferences_pb") }
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() = runBlocking { scopes.forEach { it.coroutineContext.job.cancelAndJoin() } }

    private fun open(): DataStoreUserPreferencesRepository {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        return DataStoreUserPreferencesRepository(DataStoreUserPreferencesRepository.createDataStore(scope) { file })
    }

    @Test
    fun withNothingStored_cardsAreSortedByName() = runBlocking {
        assertThat(open().cardSort.first()).isEqualTo(CardSort.NAME)
    }

    @Test
    fun aChosenSort_isReadBack() = runBlocking {
        val repository = open()

        repository.setCardSort(CardSort.MOST_USED)

        assertThat(repository.cardSort.first()).isEqualTo(CardSort.MOST_USED)
    }

    @Test
    fun aChosenSort_survivesARestart() = runBlocking {
        open().setCardSort(CardSort.RECENTLY_USED)
        // The process ends: its store closes. A second store on the same file while the first is open would throw.
        scopes.removeFirst().coroutineContext.job.cancelAndJoin()

        assertThat(open().cardSort.first()).isEqualTo(CardSort.RECENTLY_USED)
    }

    @Test
    fun anUnknownStoredSort_fallsBackToName() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        val dataStore = DataStoreUserPreferencesRepository.createDataStore(scope) { file }
        // E.g. a sort a later version added, after a downgrade.
        dataStore.edit { it[stringPreferencesKey("card_sort")] = "BY_COLOUR" }

        assertThat(DataStoreUserPreferencesRepository(dataStore).cardSort.first()).isEqualTo(CardSort.NAME)
    }

    @Test
    fun aCorruptFile_readsAsTheDefaults() = runBlocking {
        // Replacing the corrupt file is a second write of it, so this runs on CI (Linux) and is skipped on Windows.
        assumeFalse(System.getProperty("os.name").startsWith("Windows"))
        file.writeText("not a preferences file")

        assertThat(open().cardSort.first()).isEqualTo(CardSort.NAME)
    }
}
