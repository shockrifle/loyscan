package com.bdaniel.loyscan

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bdaniel.loyscan.data.local.db.AppDatabase
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.ScannedBarcode
import com.bdaniel.loyscan.testing.FakePictureReader
import com.bdaniel.loyscan.testing.aStoreEntity
import com.bdaniel.loyscan.ui.scanner.PictureScan
import com.google.common.truth.Truth.assertThat
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
 * A picture shared to LoyScan (#77), end to end: `MainActivity` started (or reached) by the share, with real Hilt,
 * navigation and Room, and a [FakePictureReader] in place of zxing-cpp (`FakePictureReaderModule`).
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp")
@RunWith(AndroidJUnit4::class)
class ShareToLoyScanTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var pictureReader: FakePictureReader

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking { db.storeDao().insert(aStoreEntity(name = "Test Store", logo = null)) }
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun sharingAPictureWithABarcode_opensThePicker_andPickingAStoreOpensTheFormWithIt() {
        pictureReader.result = PictureScan.Found(EAN_13_SCAN)

        scenario = ActivityScenario.launch(share())
        waitForText("Choose store")
        composeRule.onNodeWithText("Test Store").performClick()

        waitForText("Scanned · ${BarcodeFormat.EAN_13.displayName}")
        composeRule.onNodeWithText("Scan card").assertDoesNotExist()
        assertThat(pictureReader.reads).containsExactly(Uri.parse(PICTURE))
    }

    @Test
    fun backFromThePickerOfAShare_returnsToTheCardList() {
        pictureReader.result = PictureScan.Found(EAN_13_SCAN)
        scenario = ActivityScenario.launch(share())
        waitForText("Choose store")

        composeRule.onNodeWithContentDescription("Back").performClick()

        waitForText("My Cards")
    }

    @Test
    fun aSharedPictureWithoutABarcode_saysSo_andTheNumberCanBeTypedForAPickedStore() {
        pictureReader.result = PictureScan.NoBarcode
        scenario = ActivityScenario.launch(share())
        waitForText("Enter the number by hand")

        composeRule.onNodeWithText("Enter the number by hand").performClick()
        waitForText("Choose store")
        composeRule.onNodeWithText("Test Store").performClick()

        waitForText("Card number")
        composeRule.onNodeWithText("Scan card").assertDoesNotExist()
    }

    @Test
    fun aShareWhileLoyScanIsOpen_startsTheSameFlow() {
        pictureReader.result = PictureScan.Found(EAN_13_SCAN)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForText("My Cards")

        scenario!!.onActivity { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, share()) }

        waitForText("Choose store")
        composeRule.onNodeWithText("Test Store").performClick()
        waitForText("Scanned · ${BarcodeFormat.EAN_13.displayName}")
    }

    @Test
    fun recreatingTheActivity_neitherReadsThePictureAgainNorLosesItsBarcode() {
        pictureReader.result = PictureScan.Found(EAN_13_SCAN)
        scenario = ActivityScenario.launch(share())
        waitForText("Choose store")

        scenario!!.recreate()

        waitForText("Choose store")
        composeRule.onNodeWithText("Test Store").performClick()
        waitForText("Scanned · ${BarcodeFormat.EAN_13.displayName}")
        assertThat(pictureReader.reads).hasSize(1)
    }

    private fun share() = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        .setAction(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(PICTURE))

    private fun waitForText(text: String) =
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
}

private const val PICTURE = "content://media/picker/0/42"
private val EAN_13_SCAN = ScannedBarcode("5901234123457", BarcodeFormat.EAN_13)

// waitUntil's 1 s default timed out on a slow CI runner (#56).
private const val WAIT_TIMEOUT_MS = 10_000L
