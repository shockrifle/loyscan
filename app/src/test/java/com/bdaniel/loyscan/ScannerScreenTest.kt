package com.bdaniel.loyscan

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.ScannedBarcode
import com.bdaniel.loyscan.testing.FakeCameraPermission
import com.bdaniel.loyscan.testing.FakePictureReader
import com.bdaniel.loyscan.testing.ShownTexts
import com.bdaniel.loyscan.ui.scanner.CameraEvents
import com.bdaniel.loyscan.ui.scanner.CameraPermission.Status
import com.bdaniel.loyscan.ui.scanner.PicturePicker
import com.bdaniel.loyscan.ui.scanner.PictureScan
import com.bdaniel.loyscan.ui.scanner.ScannerCamera
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The real camera can't run on Robolectric: a fake [ScannerCamera] records what the screen asks of it. */
@RunWith(AndroidJUnit4::class)
class ScannerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var events: CameraEvents? = null
    private var torchOn: Boolean? = null
    private val fakeCamera: ScannerCamera = { torch, cameraEvents, modifier ->
        torchOn = torch
        events = cameraEvents
        Box(modifier.testTag("camera"))
    }

    private val pictureReader = FakePictureReader()

    private val scanned = mutableListOf<ScannedBarcode>()
    private var manualEntries = 0
    private var backs = 0
    private val directions = ScannerDirections(
        onScanned = { scanned += it },
        toManualEntry = { manualEntries++ },
        toBack = { backs++ },
    )

    /** The "picked" picture: each pick hands it to the screen's ViewModel, as the photo picker would; `null` cancels. */
    private var pickedPicture: Uri? = Uri.parse("content://media/picker/0/42")
    private var picks = 0
    private lateinit var viewModel: ScannerViewModel
    private val fakePicker = PicturePicker {
        picks++
        viewModel.onPicturePicked(pickedPicture)
    }

    private fun setScreen(permission: FakeCameraPermission, hintDelay: Duration = 8.seconds) {
        viewModel = ScannerViewModel(hintDelay, pictureReader)
        composeRule.setContent {
            ScannerScreen(
                directions = directions,
                viewModel = viewModel,
                permission = permission,
                camera = fakeCamera,
                picker = fakePicker,
            )
        }
    }

    @Test
    fun permissionGranted_showsTheCamera() {
        setScreen(FakeCameraPermission(Status.GRANTED))

        composeRule.onNodeWithTag("camera").assertExists()
    }

    @Test
    fun permissionNotAskedYet_isRequestedOnEntry() {
        val permission = FakeCameraPermission(Status.NOT_ASKED)

        setScreen(permission)

        composeRule.runOnIdle { assertThat(permission.requests).isEqualTo(1) }
        composeRule.onNodeWithTag("camera").assertDoesNotExist()
    }

    @Test
    fun permissionDenied_explainsWhyAndAsksAgain() {
        val permission = FakeCameraPermission(Status.DENIED)
        setScreen(permission)

        composeRule.onNodeWithText("LoyScan needs the camera", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Allow camera").performClick()

        assertThat(permission.requests).isEqualTo(1)
        composeRule.onNodeWithTag("camera").assertDoesNotExist()
    }

    @Test
    fun permissionDeniedForGood_opensTheSettings() {
        val permission = FakeCameraPermission(Status.DENIED_PERMANENTLY)
        setScreen(permission)

        composeRule.onNodeWithText("Open settings").performClick()

        assertThat(permission.settingsOpened).isEqualTo(1)
        assertThat(permission.requests).isEqualTo(0)
    }

    @Test
    fun grantingThePermission_showsTheCamera() {
        val permission = FakeCameraPermission(Status.DENIED)
        setScreen(permission)

        permission.status = Status.GRANTED

        composeRule.onNodeWithTag("camera").assertExists()
    }

    @Test
    fun enterManually_isOfferedWithTheCamera() {
        setScreen(FakeCameraPermission(Status.GRANTED))

        composeRule.onNodeWithText("Enter manually").performClick()

        assertThat(manualEntries).isEqualTo(1)
    }

    @Test
    fun enterManually_isOfferedWithoutTheCamera() {
        setScreen(FakeCameraPermission(Status.DENIED_PERMANENTLY))

        composeRule.onNodeWithText("Enter manually").performClick()

        assertThat(manualEntries).isEqualTo(1)
    }

    @Test
    fun back_leavesTheScanner() {
        setScreen(FakeCameraPermission(Status.GRANTED))

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backs).isEqualTo(1)
    }

    @Test
    fun scans_handOnTheFirstOneOnly() {
        setScreen(FakeCameraPermission(Status.GRANTED))
        val first = ScannedBarcode("5901234123457", BarcodeFormat.EAN_13)

        composeRule.runOnIdle {
            events!!.onScanned(first)
            events!!.onScanned(ScannedBarcode("other", BarcodeFormat.QR_CODE))
        }

        composeRule.runOnIdle { assertThat(scanned).containsExactly(first) }
    }

    @Test
    fun torch_isOfferedOnlyWhenTheCameraHasOne() {
        setScreen(FakeCameraPermission(Status.GRANTED))
        composeRule.onNodeWithContentDescription("Turn on torch").assertDoesNotExist()

        composeRule.runOnIdle { events!!.onStarted(false) }
        composeRule.onNodeWithContentDescription("Turn on torch").assertDoesNotExist()

        composeRule.runOnIdle { events!!.onStarted(true) }
        composeRule.onNodeWithContentDescription("Turn on torch").assertIsDisplayed()
    }

    @Test
    fun torchButton_switchesTheCamerasTorch() {
        setScreen(FakeCameraPermission(Status.GRANTED))
        composeRule.runOnIdle { events!!.onStarted(true) }

        composeRule.onNodeWithContentDescription("Turn on torch").performClick()
        composeRule.runOnIdle { assertThat(torchOn).isTrue() }

        composeRule.onNodeWithContentDescription("Turn off torch").performClick()
        composeRule.runOnIdle { assertThat(torchOn).isFalse() }
    }

    @Test
    fun hint_showsOnceTheCameraHasRunAWhile() {
        setScreen(FakeCameraPermission(Status.GRANTED), hintDelay = Duration.ZERO)
        composeRule.onNodeWithText("Card won't scan?", substring = true).assertDoesNotExist()

        composeRule.runOnIdle { events!!.onStarted(false) }

        composeRule.onNodeWithText("Card won't scan?", substring = true).assertIsDisplayed()
    }

    @Test
    fun cameraFailure_isExplained() {
        setScreen(FakeCameraPermission(Status.GRANTED))

        composeRule.runOnIdle { events!!.onFailed() }

        composeRule.onNodeWithText("The camera couldn't be opened", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Enter manually").assertIsDisplayed()
    }

    @Test
    fun cameraFailure_hidesTheScanningHint() {
        setScreen(FakeCameraPermission(Status.GRANTED), hintDelay = Duration.ZERO)
        composeRule.runOnIdle { events!!.onStarted(false) }

        composeRule.runOnIdle { events!!.onFailed() }

        composeRule.onNodeWithText("Card won't scan?", substring = true).assertDoesNotExist()
    }

    @Test
    fun cameraUnavailableAfterOpening_isExplained_withTheCameraKept() { // #40: so it can come back
        setScreen(FakeCameraPermission(Status.GRANTED), hintDelay = Duration.ZERO)
        composeRule.runOnIdle { events!!.onStarted(true) }

        composeRule.runOnIdle { events!!.onAvailable(false) }

        composeRule.onNodeWithText("The camera isn't available", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Enter manually").assertIsDisplayed()
        composeRule.onNodeWithTag("camera").assertExists()
        composeRule.onNodeWithText("Card won't scan?", substring = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Turn on torch").assertDoesNotExist()
    }

    @Test
    fun cameraAvailableAgain_showsThePreviewAgain() { // #40
        setScreen(FakeCameraPermission(Status.GRANTED))
        composeRule.runOnIdle { events!!.onStarted(true) }
        composeRule.runOnIdle { events!!.onAvailable(false) }

        composeRule.runOnIdle { events!!.onAvailable(true) }

        composeRule.onNodeWithText("The camera isn't available", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("camera").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Turn on torch").assertIsDisplayed()
    }

    @Test
    fun scanFromImage_isOfferedWithTheCamera() { // #76
        setScreen(FakeCameraPermission(Status.GRANTED))

        composeRule.onNodeWithText("Scan from image").performClick()

        assertThat(picks).isEqualTo(1)
    }

    @Test
    fun scanFromImage_isOfferedWithoutTheCameraPermission() {
        setScreen(FakeCameraPermission(Status.DENIED_PERMANENTLY))

        composeRule.onNodeWithText("Scan from image").performClick()

        assertThat(picks).isEqualTo(1)
    }

    @Test
    fun scanFromImage_isOfferedAfterACameraFailure() {
        setScreen(FakeCameraPermission(Status.GRANTED))
        composeRule.runOnIdle { events!!.onFailed() }

        composeRule.onNodeWithText("Scan from image").performClick()

        assertThat(picks).isEqualTo(1)
    }

    @Test
    fun scanFromImage_isOfferedWhileTheCameraIsUnavailable() {
        setScreen(FakeCameraPermission(Status.GRANTED))
        composeRule.runOnIdle { events!!.onAvailable(false) }

        composeRule.onNodeWithText("Scan from image").performClick()

        assertThat(picks).isEqualTo(1)
    }

    @Test
    fun aPictureWithABarcode_isHandedOnAsAScan() {
        val barcode = ScannedBarcode("5901234123457", BarcodeFormat.EAN_13)
        pictureReader.result = PictureScan.Found(barcode)
        setScreen(FakeCameraPermission(Status.GRANTED))

        composeRule.onNodeWithText("Scan from image").performClick()

        composeRule.runOnIdle { assertThat(scanned).containsExactly(barcode) }
    }

    @Test
    fun aPictureWithoutABarcode_saysSo_andKeepsTheScannerOpen() {
        pictureReader.result = PictureScan.NoBarcode
        setScreen(FakeCameraPermission(Status.GRANTED))

        composeRule.onNodeWithText("Scan from image").performClick()

        composeRule.onNodeWithText("No barcode was found in that picture", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Enter manually").assertIsDisplayed()
        composeRule.onNodeWithTag("camera").assertExists()
        composeRule.runOnIdle { assertThat(scanned).isEmpty() }
    }

    @Test
    fun aPictureThatCannotBeRead_saysSo() {
        pictureReader.result = PictureScan.Unreadable
        setScreen(FakeCameraPermission(Status.DENIED))

        composeRule.onNodeWithText("Scan from image").performClick()

        composeRule.onNodeWithText("That picture couldn't be opened", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Enter manually").assertIsDisplayed()
    }

    @Test
    fun cancellingThePicker_leavesTheScannerAsItWas() {
        pickedPicture = null
        setScreen(FakeCameraPermission(Status.GRANTED))

        composeRule.onNodeWithText("Scan from image").performClick()

        composeRule.onNodeWithText("Scan from image").assertIsEnabled()
        composeRule.onNodeWithTag("camera").assertExists()
        composeRule.runOnIdle {
            assertThat(pictureReader.reads).isEmpty()
            assertThat(scanned).isEmpty()
        }
    }

    @Test
    fun whileAPictureIsRead_scanFromImageWaits() {
        pictureReader.gate = CompletableDeferred()
        setScreen(FakeCameraPermission(Status.GRANTED))

        composeRule.onNodeWithText("Scan from image").performClick()

        composeRule.onNodeWithText("Reading the picture…").assertIsNotEnabled()
    }

    @Test
    fun aPictureReadAfterACameraScan_doesNotNavigateAgain() { // first scan only
        val first = ScannedBarcode("5901234123457", BarcodeFormat.EAN_13)
        pictureReader.result = PictureScan.Found(ScannedBarcode("other", BarcodeFormat.QR_CODE))
        setScreen(FakeCameraPermission(Status.GRANTED))
        composeRule.runOnIdle { events!!.onScanned(first) }

        composeRule.onNodeWithText("Scan from image").performClick()

        composeRule.runOnIdle { assertThat(scanned).containsExactly(first) }
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_withTheCamera_isTranslated() { // #72
        setScreen(FakeCameraPermission(Status.GRANTED), hintDelay = Duration.ZERO)
        val shown = ShownTexts(composeRule)

        composeRule.runOnIdle { events!!.onStarted(true) }
        shown.collect()
        composeRule.onNodeWithContentDescription("Zseblámpa bekapcsolása").performClick()
        shown.collect()
        pictureReader.gate = CompletableDeferred() // never completed: the picture stays "being read"
        composeRule.onNodeWithText("Beolvasás képről").performClick()
        shown.collect()

        shown.assertEveryTextIsTranslated()
        shown.assertSeen(
            "Kártya beolvasása",
            "Vissza",
            "Zseblámpa bekapcsolása",
            "Zseblámpa kikapcsolása",
            "Nem olvassa be a kártyát? Tartsd laposan és mozdulatlanul, kb. 15 cm-re a kamerától, jó fényben. " +
                "Próbáld a zseblámpával, vagy írd be a számot kézzel.",
            "Beolvasás képről",
            "Beírás kézzel",
            "A kép beolvasása…",
        )
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_withoutThePermission_asksForIt() { // #72
        setScreen(FakeCameraPermission(Status.DENIED))
        val shown = ShownTexts(composeRule)

        shown.collect()

        shown.assertEveryTextIsTranslated()
        shown.assertSeen(
            "A kártyád vonalkódjának beolvasásához a LoyScannek szüksége van a kamerára. A képeket a telefonod " +
                "olvassa be, és sehol nem tárolja, sehova nem küldi el őket.",
            "Kamera engedélyezése",
        )
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_permissionDeniedForGood_offersTheSettings() { // #72
        setScreen(FakeCameraPermission(Status.DENIED_PERMANENTLY))
        val shown = ShownTexts(composeRule)

        shown.collect()

        shown.assertEveryTextIsTranslated()
        shown.assertSeen(
            "A LoyScan nem férhet hozzá a kamerához. Az alkalmazás beállításaiban engedélyezheted.",
            "Beállítások megnyitása",
        )
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_aCameraFailure_saysSo() { // #72
        setScreen(FakeCameraPermission(Status.GRANTED))
        val shown = ShownTexts(composeRule)

        composeRule.runOnIdle { events!!.onFailed() }
        shown.collect()

        shown.assertEveryTextIsTranslated()
        shown.assertSeen("Nem sikerült megnyitni a kamerát. Írd be inkább a kártyán lévő számot.")
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_anUnavailableCamera_saysSo() { // #72
        setScreen(FakeCameraPermission(Status.GRANTED))
        val shown = ShownTexts(composeRule)

        composeRule.runOnIdle { events!!.onStarted(true) }
        composeRule.runOnIdle { events!!.onAvailable(false) }
        shown.collect()

        shown.assertEveryTextIsTranslated()
        shown.assertSeen(
            "A kamera nem érhető el. Lehet, hogy egy másik alkalmazás használja. Írd be inkább a kártyán lévő számot."
        )
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_aPictureWithoutABarcode_saysSo() { // #72
        pictureReader.result = PictureScan.NoBarcode
        setScreen(FakeCameraPermission(Status.GRANTED))
        val shown = ShownTexts(composeRule)

        composeRule.onNodeWithText("Beolvasás képről").performClick()
        shown.collect()

        shown.assertEveryTextIsTranslated()
        shown.assertSeen("Ezen a képen nem található vonalkód. Próbálj ki egy másik képet, vagy írd be a számot kézzel.")
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_aPictureThatCannotBeRead_saysSo() { // #72
        pictureReader.result = PictureScan.Unreadable
        setScreen(FakeCameraPermission(Status.GRANTED))
        val shown = ShownTexts(composeRule)

        composeRule.onNodeWithText("Beolvasás képről").performClick()
        shown.collect()

        shown.assertEveryTextIsTranslated()
        shown.assertSeen("Nem sikerült megnyitni a képet. Próbálj ki egy másik képet, vagy írd be a számot kézzel.")
    }
}
