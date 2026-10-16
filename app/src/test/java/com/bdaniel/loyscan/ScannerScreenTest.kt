package com.bdaniel.loyscan

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.ScannedBarcode
import com.bdaniel.loyscan.testing.FakeCameraPermission
import com.bdaniel.loyscan.ui.scanner.CameraEvents
import com.bdaniel.loyscan.ui.scanner.CameraPermission.Status
import com.bdaniel.loyscan.ui.scanner.ScannerCamera
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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

    private val scanned = mutableListOf<ScannedBarcode>()
    private var manualEntries = 0
    private var backs = 0
    private val directions = ScannerDirections(
        onScanned = { scanned += it },
        toManualEntry = { manualEntries++ },
        toBack = { backs++ },
    )

    private fun setScreen(permission: FakeCameraPermission, hintDelay: Duration = 8.seconds) {
        composeRule.setContent {
            ScannerScreen(
                directions = directions,
                viewModel = ScannerViewModel(hintDelay),
                permission = permission,
                camera = fakeCamera,
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
}
