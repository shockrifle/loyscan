package com.bdaniel.loyscan

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bdaniel.loyscan.data.local.db.AppDatabase
import com.bdaniel.loyscan.testing.CARD_ID
import com.bdaniel.loyscan.testing.aLoyaltyCardEntity
import com.bdaniel.loyscan.testing.aStoreEntity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * A home-screen widget tile opens its card (#11), end to end: `MainActivity` started by the open-card intent with real
 * Hilt, navigation and Room. A cold launch is the dead-process case the issue asks about — nothing of the app is alive
 * when the intent arrives.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp")
@RunWith(AndroidJUnit4::class)
class WidgetTapOpensCardTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject
    lateinit var db: AppDatabase

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            db.storeDao().insert(aStoreEntity(name = "Test Store", color = "#336699"))
            db.loyaltyCardDao().insert(aLoyaltyCardEntity(label = "My loyalty card"))
        }
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun aColdOpenCardIntent_opensThatCardsDetail_fromADeadProcess() {
        val intent = openCardIntent(ApplicationProvider.getApplicationContext(), CARD_ID, "#336699")

        scenario = ActivityScenario.launch(intent)

        waitForText("Test Store") // the detail top bar is the store name — it is not shown on the list tile
        composeRule.onNodeWithText("My loyalty card").assertExists()
    }

    @Test
    fun aWarmOpenCardIntent_opensTheCard_whileTheAppIsAlreadyRunning() {
        scenario = ActivityScenario.launch(Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java))
        waitForText("My Cards") // the app opens on the card list

        val intent = openCardIntent(ApplicationProvider.getApplicationContext(), CARD_ID, "#336699")
        scenario!!.onActivity { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, intent) }

        waitForText("Test Store")
        composeRule.onNodeWithText("My loyalty card").assertExists()
    }

    private fun waitForText(text: String) =
        composeRule.waitUntil(10_000L) { composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
}
