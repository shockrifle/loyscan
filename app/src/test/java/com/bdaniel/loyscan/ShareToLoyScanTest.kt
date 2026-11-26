package com.bdaniel.loyscan

import com.bdaniel.loyscan.ui.STORE_LOGO_PREVIEW_TAG
import com.bdaniel.loyscan.ui.CARD_TILE_LOGO_TAG
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bdaniel.loyscan.data.local.db.AppDatabase
import com.bdaniel.loyscan.data.repository.StoreRepository
import com.bdaniel.loyscan.di.CardPhotosModule
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.ScannedBarcode
import com.bdaniel.loyscan.testing.FakePictureReader
import com.bdaniel.loyscan.testing.WAIT_TIMEOUT_MS
import com.bdaniel.loyscan.testing.aStoreEntity
import com.bdaniel.loyscan.testing.writeOnePixelPng
import com.bdaniel.loyscan.ui.scanner.PictureScan
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File
import javax.inject.Inject

/**
 * A picture shared to LoyScan (#77), end to end: `ShareActivity` started (or reached) by the share (#189), with real Hilt,
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

    @Inject
    lateinit var stores: StoreRepository

    private var scenario: ActivityScenario<ShareActivity>? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking { db.storeDao().insert(aStoreEntity(name = "Test Store", logo = null)) }
    }

    @After
    fun tearDown() {
        scenario?.close()
        photoDirectory().deleteRecursively()
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
    fun aShareReachingTheOpenLoyScan_keepsItsForm_andOpensInALoyScanOfItsOwn() { // #83
        pictureReader.result = PictureScan.Found(EAN_13_SCAN)
        scenario = ActivityScenario.launch(share())
        waitForText("Choose store")
        composeRule.onNodeWithText("Test Store").performClick()
        waitForText("Label (optional)")
        composeRule.onNodeWithText("Label (optional)").performTextInput("Half-filled")

        // A sender's FLAG_ACTIVITY_SINGLE_TOP reaches the open activity even in standard mode.
        scenario!!.onActivity { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, share()) }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Half-filled").assertExists()
        composeRule.onNodeWithText("Choose store").assertDoesNotExist()
        val forwarded = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertThat(forwarded.component?.className).isEqualTo(ShareActivity::class.java.name)
        assertThat(forwarded.sharedPicture()).isEqualTo(Uri.parse(PICTURE))
        assertThat(forwarded.flags and SHARE_IN_A_TASK_OF_ITS_OWN).isEqualTo(SHARE_IN_A_TASK_OF_ITS_OWN)
        assertThat(forwarded.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP).isEqualTo(0)
        assertThat(pictureReader.reads).hasSize(1)
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

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // the picture is really decoded, cropped and encoded
    fun sharingAPictureAsAStoreLogo_asksWhichStore_andTheCropMakesItItsLogo() { // #151
        scenario = ActivityScenario.launch(logoShare())
        waitForText("Logo for which store?")
        composeRule.onNodeWithText("Test Store").performClick()

        waitForText("Crop")
        composeRule.onNodeWithText("Crop").performClick()

        waitForText("Remove logo")
        composeRule.onNode(hasTestTag(CARD_TILE_LOGO_TAG) and hasAnyAncestor(hasTestTag(STORE_LOGO_PREVIEW_TAG)), useUnmergedTree = true).assertExists()
        val logo = runBlocking { stores.getAll().first().single { it.name == "Test Store" }.logo }
        assertThat(logo).isNotNull()
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForText("No cards yet", substring = true)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // the SVG is really drawn, cropped and encoded
    fun sharingAnSvgAsAStoreLogo_theCropMakesItItsLogo() { // #211
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect width="100" height="100" fill="red"/></svg>"""
        scenario = ActivityScenario.launch(logoShare(svg.toByteArray(), "image/svg+xml"))
        waitForText("Logo for which store?")
        composeRule.onNodeWithText("Test Store").performClick()

        waitForText("Crop")
        composeRule.onNodeWithText("Crop").performClick()

        waitForText("Remove logo")
        val logo = runBlocking { stores.getAll().first().single { it.name == "Test Store" }.logo }
        val drawn = BitmapFactory.decodeFile(File(photoDirectory(), logo!!).path)
        assertThat(drawn.getPixel(drawn.width / 2, drawn.height / 2)).isEqualTo(Color.RED)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun recreatingTheActivity_keepsTheStoreQuestion_withoutCopyingThePictureAgain() { // #151
        scenario = ActivityScenario.launch(logoShare())
        waitForText("Logo for which store?")

        scenario!!.recreate()

        waitForText("Logo for which store?")
        assertThat(photoDirectory().listFiles().orEmpty()).hasLength(1)
        assertThat(pictureReader.reads).isEmpty() // not read for a barcode
    }

    /**
     * A share through "Set as store logo" (#151), its picture served by the content resolver: [picture] of [type], a real
     * PNG by default.
     */
    private fun logoShare(picture: ByteArray? = null, type: String = "image/png"): Intent {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val bytes = picture ?: File(context.cacheDir, "shared.png").writeOnePixelPng().readBytes()
        shadowOf(context.contentResolver).registerInputStream(Uri.parse(PICTURE), ByteArrayInputStream(bytes))
        return share().setType(type).setComponent(ComponentName(context, SHARE_AS_STORE_LOGO))
    }

    private fun photoDirectory() =
        File(ApplicationProvider.getApplicationContext<Application>().filesDir, CardPhotosModule.PHOTO_DIRECTORY)

    private fun share() = Intent(ApplicationProvider.getApplicationContext(), ShareActivity::class.java)
        .setAction(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(PICTURE))

    private fun waitForText(text: String, substring: Boolean = false) = composeRule.waitUntil(WAIT_TIMEOUT_MS) {
        composeRule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }
}

private const val PICTURE = "content://media/picker/0/42"
private const val SHARE_IN_A_TASK_OF_ITS_OWN =
    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
private val EAN_13_SCAN = ScannedBarcode("5901234123457", BarcodeFormat.EAN_13)
