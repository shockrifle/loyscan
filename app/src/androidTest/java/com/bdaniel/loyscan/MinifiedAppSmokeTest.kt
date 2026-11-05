package com.bdaniel.loyscan

import android.Manifest
import android.content.Intent
import android.os.SystemClock
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The release build after R8 (#73), driven end to end on a device: add a card by typing (with a new custom store),
 * open it, edit it, delete it, and run the scanner's camera on the way. Run against the `smoke` build type, which is the
 * release build signed with the debug key: `./gradlew connectedSmokeAndroidTest` (wiki/workflows/build-and-run.md).
 *
 * The app is driven from outside through UiAutomator, by the text and content descriptions the user sees, so the test
 * depends on none of the app's classes, which R8 has renamed or removed. The run uninstalls the smoke app afterwards,
 * so the store and card it adds don't outlive it. A failed wait lists what was on screen and leaves a screenshot in
 * `/data/local/tmp/smoke-failure.png` (`adb pull` it).
 */
@RunWith(AndroidJUnit4::class)
class MinifiedAppSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val packageName = instrumentation.targetContext.packageName

    @Before
    fun launchTheApp() {
        // The scanner then opens the camera rather than asking; the camera preview opening is part of the check.
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.CAMERA)
        val launch = instrumentation.targetContext.packageManager.getLaunchIntentForPackage(packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        instrumentation.targetContext.startActivity(launch)
        await(By.text("My Cards"))
    }

    @Test
    fun aCardTyped_opens_isEdited_andIsDeleted() {
        // Add: a custom store, the scanner for it, then the number typed in.
        click(By.desc("Add card"))
        click(By.text("Other card"))
        await(By.text("New store"))
        fields()[0].text = STORE
        click(By.text("Create"))
        await(By.text("Scan card"))
        // Stay while the camera runs: each frame goes through the analyzer and zxing-cpp's JNI read (no barcode in
        // view, so it finds none). A crash there under R8 ends the process, and the scanner is no longer on screen.
        SystemClock.sleep(CAMERA_FRAMES_MS)
        await(By.text("Scan card"))
        click(By.text("Enter manually"))
        await(By.text("Save"))
        fields()[0].text = NUMBER
        fields()[1].text = LABEL
        click(By.text("Save"))

        // The new card opens.
        await(By.text("Card added"))
        await(By.text(LABEL))
        await(By.text(NUMBER))

        // Edit its label.
        click(By.desc("Edit"))
        await(By.text("Save"))
        fields()[1].text = EDITED_LABEL
        click(By.text("Save"))
        await(By.text(EDITED_LABEL))

        // Delete it: back on the list, which has no card left.
        click(By.desc("Delete"))
        await(By.text("Delete this card?"))
        click(By.text("Delete"))
        await(By.text("My Cards"))
        await(By.text("No cards yet"))
    }

    /** The screen's text fields, top to bottom, once there is one. */
    private fun fields(): List<UiObject2> {
        await(By.clazz(EditText::class.java))
        return device.findObjects(By.clazz(EditText::class.java))
    }

    private fun click(selector: BySelector) = await(selector).click()

    private fun await(selector: BySelector): UiObject2 =
        device.wait(Until.findObject(selector), TIMEOUT_MS)
            ?: run {
                instrumentation.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/smoke-failure.png").close()
                throw AssertionError("Not on screen: $selector. On screen: ${onScreen()}")
            }

    /** What the screen shows, for a failure message: every text and content description, top to bottom. */
    private fun onScreen(): String = device.findObjects(By.pkg(packageName))
        .flatMap { listOfNotNull(it.text, it.contentDescription) }
        .joinToString(" | ")

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val CAMERA_FRAMES_MS = 3_000L
        const val STORE = "Smoke Test Store"
        const val NUMBER = "12345678"
        const val LABEL = "Smoke card"
        const val EDITED_LABEL = "Smoke card edited"
    }
}
