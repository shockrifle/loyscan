package com.bdaniel.loyscan

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
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
import java.io.File

/**
 * The release build after R8 (#73), driven end to end on a device: add a card by typing (with a new custom store),
 * open it, edit it, delete it, and run the scanner's camera on the way; and read a shared picture of a barcode into the
 * card form (#105), so zxing-cpp decodes a barcode and builds its result under R8. Run against the `smoke` build type,
 * which is the release build signed with the debug key: `./gradlew connectedSmokeAndroidTest`
 * (wiki/workflows/build-and-run.md).
 *
 * The app is driven from outside through UiAutomator, by the text and content descriptions the user sees, so the test
 * depends on none of the app's classes, which R8 has renamed or removed. A text the app has as a string resource is
 * looked up by its name ([string]), so the test passes on a phone in Hungarian too (#72); the others are still English. The run uninstalls the smoke app afterwards,
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
        await(By.text(string("card_list_title")))
    }

    @Test
    fun aCardTyped_opens_isEdited_andIsDeleted() {
        // Add: a custom store, the scanner for it, then the number typed in.
        click(By.desc(string("add_card")))
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
        await(By.text(string("card_added")))
        await(By.text(LABEL))
        await(By.text(NUMBER))

        // Edit its label.
        click(By.desc(string("edit")))
        await(By.text("Save"))
        fields()[1].text = EDITED_LABEL
        click(By.text("Save"))
        await(By.text(EDITED_LABEL))

        // Delete it: back on the list, which has no card left.
        click(By.desc(string("delete")))
        await(By.text(plural("delete_cards_question", 1)))
        click(By.text(string("delete")))
        await(By.text(string("card_list_title")))
        await(By.text(string("no_cards_yet")))
    }

    @Test
    fun aSharedPictureOfABarcode_isReadIntoTheCardForm() {
        // #105: a barcode zxing-cpp has to decode, which the camera's empty frames never give it. The picture is shared
        // as the system chooser shares one (#84), through the app's own FileProvider: the photo directory is the one
        // folder it serves, and a content:// URI is the only kind a share is read from.
        val picture = File(instrumentation.targetContext.filesDir, "card_photos/smoke-barcode.png")
        try {
            picture.parentFile!!.mkdirs()
            picture.outputStream().use { ean13Picture(BARCODE).compress(Bitmap.CompressFormat.PNG, 100, it) }
            val share = Intent(Intent.ACTION_SEND)
                .setPackage(packageName) // as the chooser sends it once LoyScan is picked, so no app chooser opens
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://$packageName.photos/card_photos/${picture.name}"))
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            instrumentation.targetContext.startActivity(share)

            // The picker holds the scan; "Other card" carries it on to a new store's form.
            click(By.text("Other card"))
            await(By.text("New store"))
            fields()[0].text = SHARED_STORE
            click(By.text("Create"))
            await(By.text("Scanned · EAN-13"))
            await(By.text(BARCODE))
        } finally {
            picture.delete()
        }
    }

    /**
     * The app's string [name] as the app shows it in the device's language (#72). Looked up by name, since the app's
     * `R` class is renamed by R8; resource shrinking keeps the names (`res/raw/smoke_test_keep.xml`).
     */
    private fun string(name: String): String {
        val resources = instrumentation.targetContext.resources
        val id = resources.getIdentifier(name, "string", packageName)
        check(id != 0) { "The app has no string resource $name" }
        return resources.getString(id)
    }

    /** The app's plural [name] for [count], as [string] finds a string. */
    private fun plural(name: String, count: Int): String {
        val resources = instrumentation.targetContext.resources
        val id = resources.getIdentifier(name, "plurals", packageName)
        check(id != 0) { "The app has no plurals resource $name" }
        return resources.getQuantityString(id, count, count)
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

    /**
     * An EAN-13 barcode of [number] (13 digits, check digit included), drawn with the platform's graphics only: the
     * app's own ZXing classes are renamed by R8, so the test can't call them.
     */
    private fun ean13Picture(number: String): Bitmap {
        val digits = number.map { it.digitToInt() }
        val modules = buildString {
            append("101")
            for (i in 1..6) {
                val left = EAN_L[digits[i]]
                append(if (EAN_PARITY[digits[0]][i - 1] == 'G') left.complement().reversed() else left)
            }
            append("01010")
            for (i in 7..12) append(EAN_L[digits[i]].complement())
            append("101")
        }
        val width = (modules.length + 2 * QUIET_MODULES) * MODULE_PX
        val bitmap = Bitmap.createBitmap(width, BAR_HEIGHT_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
        val black = Paint().apply { color = Color.BLACK }
        modules.forEachIndexed { i, module ->
            if (module == '1') {
                val left = ((QUIET_MODULES + i) * MODULE_PX).toFloat()
                canvas.drawRect(left, 0f, left + MODULE_PX, BAR_HEIGHT_PX.toFloat(), black)
            }
        }
        return bitmap
    }

    private fun String.complement() = map { if (it == '1') '0' else '1' }.joinToString("")

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val CAMERA_FRAMES_MS = 3_000L
        const val STORE = "Smoke Test Store"
        const val NUMBER = "12345678"
        const val LABEL = "Smoke card"
        const val EDITED_LABEL = "Smoke card edited"
        const val SHARED_STORE = "Smoke Share Store"

        /** A valid EAN-13 (GS1's example number): 590123412345 and its check digit 7. */
        const val BARCODE = "5901234123457"

        /** EAN-13's left-hand (odd parity, "L") digit patterns; "R" is their complement, "G" the reversed "R". */
        val EAN_L = listOf(
            "0001101", "0011001", "0010011", "0111101", "0100011",
            "0110001", "0101111", "0111011", "0110111", "0001011",
        )

        /** Which of the left six digits use "L" or "G", chosen by the first digit, which isn't drawn itself. */
        val EAN_PARITY = listOf(
            "LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG",
            "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL",
        )
        const val MODULE_PX = 6
        const val QUIET_MODULES = 15
        const val BAR_HEIGHT_PX = 300
    }
}
