package com.bdaniel.loyscan.widget

import android.graphics.Bitmap
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The logos the widget draws (#181): each store's logo decoded once, scaled down to fit the logo box and never up,
 * all of them together within a memory budget, and a logo that can't be read left out (its tiles show the initials).
 * Real PNGs on a [TemporaryFolder]; NATIVE graphics, so the pictures are decoded for real.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetLogosTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val directory by lazy { temporaryFolder.newFolder("card_photos") }

    /** As `FileCardPhotos.file`: a name that isn't a plain file name throws. */
    private fun file(name: String): File {
        require(name.isNotEmpty() && name == File(name).name && name != "..") { "Not a photo name: $name" }
        return File(directory, name)
    }

    private fun logos(box: IntSize = IntSize(200, 120), budget: Long = Long.MAX_VALUE) =
        WidgetLogos(::file, box, budget)

    private fun png(name: String, width: Int, height: Int): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF2266AA.toInt()) }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return name
    }

    private fun tile(id: String, logo: String?) = WidgetTile(id, "#112233", "TE", "Tesco", label = null, logo = logo)

    private fun Bitmap.size() = IntSize(width, height)

    @Test
    fun aWideLogo_isScaledDownToFitTheBox_keepingItsShape() = runTest {
        val logo = png("wide", 1000, 500)

        val shown = logos().forTiles(listOf(tile("c1", logo)))

        assertThat(shown.getValue(logo).size()).isEqualTo(IntSize(200, 100))
    }

    @Test
    fun aSquareLogo_isScaledDownToTheBoxHeight() = runTest {
        val logo = png("square", 1000, 1000)

        val shown = logos().forTiles(listOf(tile("c1", logo)))

        assertThat(shown.getValue(logo).size()).isEqualTo(IntSize(120, 120))
    }

    @Test
    fun aSmallLogo_isNotScaledUp() = runTest {
        val logo = png("small", 50, 40)

        val shown = logos().forTiles(listOf(tile("c1", logo)))

        assertThat(shown.getValue(logo).size()).isEqualTo(IntSize(50, 40))
    }

    @Test
    fun aMissingFile_oneThatIsNotAPicture_andAnInvalidName_areLeftOut_withoutThrowing() = runTest {
        val good = png("good", 10, 10)
        File(directory, "text").writeText("not a picture")

        val shown = logos().forTiles(
            listOf(tile("c1", good), tile("c2", "missing"), tile("c3", "text"), tile("c4", "../escape"), tile("c5", null)),
        )

        assertThat(shown.keys).containsExactly(good)
    }

    @Test
    fun aStoresLogo_isDecodedOnce_forAllItsCards_andLaterCalls() = runTest {
        val logo = png("shared", 100, 100)
        val logos = logos()

        val first = logos.forTiles(listOf(tile("c1", logo), tile("c2", logo)))
        // Decoded from a stream it closed: on Windows an open file can't be deleted (#88).
        assertThat(File(directory, logo).delete()).isTrue()
        val second = logos.forTiles(listOf(tile("c1", logo), tile("c2", logo)))

        assertThat(second.getValue(logo)).isSameInstanceAs(first.getValue(logo))
    }

    @Test
    fun aLogoNoLongerShown_isForgotten() = runTest {
        val logo = png("dropped", 100, 100)
        val logos = logos()
        logos.forTiles(listOf(tile("c1", logo)))

        logos.forTiles(listOf(tile("c1", null)))
        File(directory, logo).delete()
        val shown = logos.forTiles(listOf(tile("c1", logo)))

        assertThat(shown).isEmpty()
    }

    @Test
    fun logosOverTheBudget_areAllScaledDown_toFitIt() = runTest {
        // Three 200 × 200 logos are 3 × 160,000 bytes (ARGB); a budget of two, 320,000, scales each side by √(2/3),
        // 0.8165: 163 × 163.
        val names = listOf(png("a", 1000, 1000), png("b", 1000, 1000), png("c", 1000, 1000))

        val shown = logos(box = IntSize(200, 200), budget = 320_000)
            .forTiles(names.mapIndexed { i, name -> tile("c$i", name) })

        assertThat(shown.values.map { it.size() }).containsExactly(IntSize(163, 163), IntSize(163, 163), IntSize(163, 163))
        assertThat(shown.values.sumOf { it.byteCount.toLong() }).isAtMost(320_000)
    }

    @Test
    fun logosWithinTheBudget_keepTheirBoxSize() = runTest {
        val names = listOf(png("a", 1000, 1000), png("b", 1000, 1000))

        val shown = logos(box = IntSize(200, 200), budget = 320_000).forTiles(names.mapIndexed { i, name -> tile("c$i", name) })

        assertThat(shown.values.map { it.size() }).containsExactly(IntSize(200, 200), IntSize(200, 200))
    }

    @Test
    fun theDefaultBudget_is4MB() {
        assertThat(WIDGET_LOGO_BUDGET_BYTES).isEqualTo(4L * 1024 * 1024)
    }
}
