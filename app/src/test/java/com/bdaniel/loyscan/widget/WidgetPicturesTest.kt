package com.bdaniel.loyscan.widget

import android.graphics.Bitmap
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.domain.LogoStyle
import com.bdaniel.loyscan.domain.LogoTreatment
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The pictures the widget draws: each store's logo (#181) decoded once, scaled down to fit the logo box and never up;
 * each shown front photo (#185) cut to the photo box's shape around its centre and scaled down to it, never up; all of
 * them together within a memory budget, and a picture that can't be read left out (its tiles show the logo or the
 * initials). Real PNGs on a [TemporaryFolder]; NATIVE graphics, so the pictures are decoded for real.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetPicturesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val directory by lazy { temporaryFolder.newFolder("card_photos") }

    /** As `FileCardPhotos.file`: a name that isn't a plain file name throws. */
    private fun file(name: String): File {
        require(name.isNotEmpty() && name == File(name).name && name != "..") { "Not a photo name: $name" }
        return File(directory, name)
    }

    private fun logos(box: IntSize = IntSize(200, 120), budget: Long = Long.MAX_VALUE) =
        WidgetPictures(::file, logoBox = box, photoBox = PHOTO_BOX, budgetBytes = budget)

    private fun pictures(budget: Long = Long.MAX_VALUE) =
        WidgetPictures(::file, logoBox = IntSize(200, 120), photoBox = PHOTO_BOX, budgetBytes = budget)

    private fun png(name: String, width: Int, height: Int): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF2266AA.toInt()) }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return name
    }

    private fun tile(
        id: String,
        logo: String?,
        photo: String? = null,
        logoTreatment: LogoTreatment = LogoTreatment.AS_IS,
        color: String = "#112233",
    ) = WidgetTile(id, color, "TE", "Tesco", label = null, logo = logo, photo = photo, logoTreatment = logoTreatment)

    /** A PNG of [middle] rows of [inner] colour between equal bands of [outer], or columns when [across]. */
    private fun banded(name: String, width: Int, height: Int, middle: Int, across: Boolean = false): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(OUTER) }
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { color = INNER }
        if (across) {
            val left = (width - middle) / 2f
            canvas.drawRect(left, 0f, left + middle, height.toFloat(), paint)
        } else {
            val top = (height - middle) / 2f
            canvas.drawRect(0f, top, width.toFloat(), top + middle, paint)
        }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return name
    }

    private fun Bitmap.corners() = listOf(getPixel(0, 0), getPixel(width - 1, 0), getPixel(0, height - 1), getPixel(width - 1, height - 1))

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
    fun aLogoInSingleColour_isDrawnInItsColour() = runTest { // #240
        val logo = png("logo", 100, 100)

        val shown = logos().forTiles(listOf(tile("c1", logo, logoTreatment = SINGLE_WHITE)))

        assertThat(shown.getValue(logo).getPixel(50, 50)).isEqualTo(WHITE)
    }

    @Test
    fun aLogoDrawnAnotherWay_isDrawnAgain_atTheSameSize() = runTest { // #240: a changed treatment redraws the widget
        val logo = png("logo", 100, 100)
        val logos = logos()
        val asIs = logos.forTiles(listOf(tile("c1", logo)))

        val single = logos.forTiles(listOf(tile("c1", logo, logoTreatment = SINGLE_WHITE)))

        assertThat(asIs.getValue(logo).getPixel(50, 50)).isEqualTo(0xFF2266AA.toInt())
        assertThat(single.getValue(logo).getPixel(50, 50)).isEqualTo(WHITE)
    }

    @Test
    fun aRecolouredLogo_isDrawnInItsColour() = runTest { // #240: the logo is in the tile's brand colour
        val logo = png("logo", 100, 100)

        val shown = logos().forTiles(listOf(tile("c1", logo, logoTreatment = RECOLOUR_WHITE, color = "#2266AA")))

        assertThat(shown.getValue(logo).getPixel(50, 50)).isEqualTo(WHITE)
    }

    @Test
    fun aRecolouredLogo_isDrawnAgain_whenItsBrandColourChanges() = runTest { // #240: Recolour follows the brand colour
        val logo = png("logo", 100, 100)
        val logos = logos()
        logos.forTiles(listOf(tile("c1", logo, logoTreatment = RECOLOUR_WHITE, color = "#2266AA")))

        val onRed = logos.forTiles(listOf(tile("c1", logo, logoTreatment = RECOLOUR_WHITE, color = "#AA0000")))

        assertThat(onRed.getValue(logo).getPixel(50, 50)).isEqualTo(0xFF2266AA.toInt()) // no longer the brand colour
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
    fun logosOverTheBudget_areScaledOnce_andTheSameBitmapsComeBackLater() = runTest { // #181 review
        val names = listOf(png("a", 1000, 1000), png("b", 1000, 1000), png("c", 1000, 1000))
        val tiles = names.mapIndexed { i, name -> tile("c$i", name) }
        val logos = logos(box = IntSize(200, 200), budget = 320_000)

        val first = logos.forTiles(tiles)
        val second = logos.forTiles(tiles)

        names.forEach { assertThat(second.getValue(it)).isSameInstanceAs(first.getValue(it)) }
    }

    @Test
    fun byDefault_theLogosShownTogether_stayWithin4MB() = runTest { // #181
        // Two 1024 × 1024 logos are 8 MiB (ARGB); 4 MiB scales each side by √½, 0.7071: 724 × 724.
        val names = listOf(png("a", 1024, 1024), png("b", 1024, 1024))

        val shown = WidgetPictures(::file, logoBox = IntSize(1024, 1024), photoBox = PHOTO_BOX)
            .forTiles(names.mapIndexed { i, name -> tile("c$i", name) })

        assertThat(shown.values.map { it.size() }).containsExactly(IntSize(724, 724), IntSize(724, 724))
    }

    @Test
    fun aLargeCardShapedPhoto_isScaledDownToThePhotoBox() = runTest { // #185: the binder limit
        val photo = png("photo", 2000, 1000)

        val shown = pictures().forTiles(listOf(tile("c1", logo = null, photo = photo)))

        assertThat(shown.getValue(photo).size()).isEqualTo(PHOTO_BOX)
    }

    @Test
    fun aTallPhoto_isCutAroundItsMiddleToTheBoxsShape_andScaledToIt() = runTest { // #185: "use whole photo" keeps any shape
        // 300 × 600 cut to the box's 2:1 is its middle 300 × 150, the blue band; scaled down to 200 × 100.
        val photo = banded("tall", 300, 600, middle = 150)

        val bitmap = pictures().forTiles(listOf(tile("c1", logo = null, photo = photo))).getValue(photo)

        assertThat(bitmap.size()).isEqualTo(IntSize(200, 100))
        assertThat(bitmap.corners()).containsExactly(INNER, INNER, INNER, INNER)
    }

    @Test
    fun aWidePhoto_isCutAroundItsMiddleToTheBoxsShape_andScaledToIt() = runTest { // #185
        // 2000 × 200 cut to 2:1 is its middle 400 × 200, the blue band; scaled down to 200 × 100.
        val photo = banded("wide", 2000, 200, middle = 400, across = true)

        val bitmap = pictures().forTiles(listOf(tile("c1", logo = null, photo = photo))).getValue(photo)

        assertThat(bitmap.size()).isEqualTo(IntSize(200, 100))
        assertThat(bitmap.corners()).containsExactly(INNER, INNER, INNER, INNER)
    }

    @Test
    fun aSmallPhoto_isCutToTheBoxsShape_butNotScaledUp() = runTest { // #185
        val photo = png("small", 100, 80)

        val shown = pictures().forTiles(listOf(tile("c1", logo = null, photo = photo)))

        assertThat(shown.getValue(photo).size()).isEqualTo(IntSize(100, 50))
    }

    @Test
    fun aPhotoOnAPhoneScreen_isNoLargerThanItsTile() = runTest { // #185: the binder limit, at 2.75 density
        val photo = png("portrait", 1200, 1600)
        val box = widgetPhotoBoxPx(density = 2.75f)

        val bitmap = WidgetPictures(::file, logoBox = IntSize(550, 330), photoBox = box)
            .forTiles(listOf(tile("c1", logo = null, photo = photo))).getValue(photo)

        assertThat(bitmap.width).isAtMost(box.width)
        assertThat(bitmap.height).isAtMost(box.height)
        assertThat(bitmap.width).isAtLeast(box.width - 1) // cut to the box's shape: it fills the box
    }

    @Test
    fun aPhotoThatCannotBeRead_isLeftOut_andTheTilesLogoStillComes() = runTest { // #185: the tile falls back to it
        val logo = png("logo", 10, 10)
        val otherLogo = png("other-logo", 10, 10)
        File(directory, "text").writeText("not a picture")
        // A PNG cut after its header: its size can be read, its pixels can't.
        val whole = File(directory, png("whole", 50, 50)).readBytes()
        File(directory, "cut").writeBytes(whole.copyOf(33))

        val shown = pictures().forTiles(
            listOf(
                tile("c1", logo = logo, photo = "missing"),
                tile("c2", logo = null, photo = "text"),
                tile("c3", logo = otherLogo, photo = "cut"), // readable header, so only its decode fails
                tile("c4", logo = null, photo = "../escape"),
            ),
        )

        assertThat(shown.keys).containsExactly(logo, otherLogo)
    }

    @Test
    fun photosAndLogosTogetherOverTheBudget_areAllScaledDownAlike() = runTest { // #185: one budget for both
        // A 200 × 100 photo and a 200 × 100 logo are 2 × 80,000 bytes; a budget of 80,000 scales each side by √½,
        // 0.7071: 141 × 70.
        val photo = png("photo", 400, 200)
        val logo = png("logo", 400, 200)

        val shown = WidgetPictures(::file, logoBox = IntSize(200, 100), photoBox = PHOTO_BOX, budgetBytes = 80_000)
            .forTiles(listOf(tile("c1", logo = null, photo = photo), tile("c2", logo = logo)))

        assertThat(shown.getValue(photo).size()).isEqualTo(IntSize(141, 70))
        assertThat(shown.getValue(logo).size()).isEqualTo(IntSize(141, 70))
        assertThat(shown.values.sumOf { it.byteCount.toLong() }).isAtMost(80_000)
    }

    @Test
    fun theLogoOfATileShowingItsPhoto_isNotDecoded_andTakesNoBudget() = runTest { // review of #185
        // Only the photo is drawn, so it alone counts: a 200 × 100 photo is 80,000 bytes, within the budget.
        val photo = png("photo", 400, 200)
        val logo = png("logo", 400, 200)

        val shown = WidgetPictures(::file, logoBox = IntSize(200, 100), photoBox = PHOTO_BOX, budgetBytes = 80_000)
            .forTiles(listOf(tile("c1", logo = logo, photo = photo)))

        assertThat(shown.keys).containsExactly(photo)
        assertThat(shown.getValue(photo).size()).isEqualTo(IntSize(200, 100))
    }

    @Test
    fun aPhotoThatDidNotDecode_isNotDecodedAgain_onTheNextRedraw() = runTest { // review of #185
        val logo = png("logo", 10, 10)
        val whole = File(directory, png("whole", 50, 50)).readBytes()
        File(directory, "cut").writeBytes(whole.copyOf(33))
        val pictures = pictures()
        val tiles = listOf(tile("c1", logo = logo, photo = "cut"))
        pictures.forTiles(tiles)

        // Now a picture that would decode: had the failure been forgotten, the photo would come back.
        File(directory, "cut").writeBytes(whole)
        val shown = pictures.forTiles(tiles)

        assertThat(shown.keys).containsExactly(logo)
    }

    @Test
    fun aPhotoNoLongerShown_isForgotten() = runTest { // #185: the switch turned off
        val photo = png("photo", 400, 200)
        val pictures = pictures()
        pictures.forTiles(listOf(tile("c1", logo = null, photo = photo)))

        val shown = pictures.forTiles(listOf(tile("c1", logo = null, photo = null)))

        assertThat(shown).isEmpty()
    }

    private companion object {
        /** A 2:1 photo box, so the cuts are easy to work out by hand. */
        val PHOTO_BOX = IntSize(200, 100)
        const val OUTER = 0xFFFF0000.toInt()
        const val INNER = 0xFF0000FF.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        val SINGLE_WHITE = LogoTreatment(style = LogoStyle.SINGLE_COLOUR, tint = "#FFFFFF")
        val RECOLOUR_WHITE = LogoTreatment(style = LogoStyle.RECOLOUR, tint = "#FFFFFF")
    }
}
