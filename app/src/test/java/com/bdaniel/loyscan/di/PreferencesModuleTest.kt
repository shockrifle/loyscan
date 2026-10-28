package com.bdaniel.loyscan.di

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The preferences file as the app opens it. Its name is what keeps the users' choices across updates. */
@RunWith(AndroidJUnit4::class)
class PreferencesModuleTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val file = File(context.filesDir, "datastore/user_preferences.preferences_pb")

    @After
    fun tearDown() = runBlocking {
        scope.coroutineContext.job.cancelAndJoin()
        file.delete()
        Unit
    }

    @Test
    fun theUserPreferences_areKeptInTheAppsPrivateFiles_underAStableName() = runBlocking {
        val dataStore = PreferencesModule.userPreferencesDataStore(context, scope)

        dataStore.edit { it[stringPreferencesKey("any")] = "value" }

        assertThat(file.exists()).isTrue()
    }
}
