package com.bdaniel.loyscan.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.ui.CARD_ASPECT_RATIO
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The widget's tiles as a home screen lays them out (#216): its real RemoteViews for the layout a launcher picks, shown
 * at the widget's real width ([WidgetRender]). Each test also draws what it measured into
 * `app/build/outputs/widget-renders/`, for a person to look at.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34]) // the collection path of Android 12+; before it the grid's items come from a RemoteViewsService
class CardWidgetRenderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val tiles = listOf(
        WidgetTile("c1", "#1E5AA8", "TE", "Tesco", "Gold", logo = "tesco.png"),
        WidgetTile("c2", "#D32F2F", "AL", "Aldi", "Family"),
        WidgetTile("c3", "#2E7D32", "SP", "Spar", label = null),
    )

    private val logos = mapOf("tesco.png" to Bitmap.createBitmap(300, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) })

    private val widget = WidgetRender(context) { CardWidgetContent(tiles, logos) }

    @Test
    fun aLayoutShownInAWidgetOfItsOwnWidth_hasCardTilesFillingTheirCells_inOneColumn() {
        val rendered = widget.show(layoutWidth = 205.dp, realWidth = 205.dp).also { it.savePng("layout-205-at-205") }

        // A 205dp widget: one column, a 201dp cell, a 197dp tile at the card's ratio.
        assertThat(rendered.tiles).hasSize(3)
        for (tile in rendered.tiles) {
            assertWithMessage(tile.description).that(tile.width).isWithin(rendered.pixel).of(197f)
            assertWithMessage(tile.description).that(tile.height).isWithin(rendered.pixel).of(197f / CARD_ASPECT_RATIO)
        }
    }

    @Test
    fun aLayoutShownInAWidgetOfItsOwnWidth_hasCardTilesFillingTheirCells_inTwoColumns() {
        val rendered = widget.show(layoutWidth = 286.dp, realWidth = 286.dp).also { it.savePng("layout-286-at-286") }

        // A 286dp widget: two columns of 141dp cells, 137dp tiles at the card's ratio, side by side.
        val (first, second) = rendered.tiles
        assertThat(rendered.tiles).hasSize(3)
        assertThat(second.top).isWithin(rendered.pixel).of(first.top)
        for (tile in rendered.tiles) {
            assertWithMessage(tile.description).that(tile.width).isWithin(rendered.pixel).of(137f)
            assertWithMessage(tile.description).that(tile.height).isWithin(rendered.pixel).of(137f / CARD_ASPECT_RATIO)
        }
    }

    @Test
    fun aLayoutShownInANarrowerWidget_fillsTheRealWidth() { // the mismatch the issue saw on a phone
        val rendered = widget.show(layoutWidth = 205.dp, realWidth = 150.dp).also { it.savePng("layout-205-at-150") }

        for (tile in rendered.tiles) assertWithMessage(tile.description).that(tile.width).isWithin(rendered.pixel).of(142f)
    }

    @Test
    fun aLayoutShownInANarrowerWidget_keepsTheCardsRatioAtTheRealWidth() { // #216: its height was the layout's
        val rendered = widget.show(layoutWidth = 205.dp, realWidth = 150.dp)

        for (tile in rendered.tiles) {
            assertWithMessage(tile.description).that(tile.height).isWithin(rendered.pixel).of(142f / CARD_ASPECT_RATIO)
        }
    }

    @Test
    fun aLauncherPickingTheLayoutForTheWidgetsWidth_showsSquareTilesBelow120dp_andCardTilesFrom120dp() { // #216
        assertSquareBelow120dp_cardFrom120dp(110..480)
    }

    @Test
    @Config(qualifiers = "440dpi") // a phone's 2.75 density: tiles are whole pixels, the sizes in dp are not
    fun atAPhonesDensity_tilesAreSquareBelow120dp_andCardTilesFrom120dp() { // #216
        assertSquareBelow120dp_cardFrom120dp(110..480 step 3)
    }

    @Test
    fun theNarrowestWidget_atItsLowestHeight_showsOneWholeSquareTile() { // #216: minResizeHeight
        val rendered = widget.show(layoutWidth = 110.dp, realWidth = 110.dp, realHeight = 110.dp)
            .also { it.savePng("layout-110-at-110x110") }

        val tile = rendered.tiles.first()
        assertThat(tile.width).isWithin(rendered.pixel).of(102f)
        assertThat(tile.height).isWithin(rendered.pixel).of(102f)
        assertThat(tile.bottom).isAtMost(110f - 4f + rendered.pixel)
    }

    @Test
    fun anyLayoutShownAtAnyWidth_neverDrawsATileTallerThanItIsWide() { // #216, whatever layout a launcher picks
        for (layout in WIDGET_SIZES.map { it.width }) {
            for (realWidth in 110..480 step 5) {
                val rendered = widget.show(layout, realWidth.dp)
                assertThat(rendered.tiles).hasSize(3)
                for (tile in rendered.tiles) {
                    assertWithMessage("${tile.description}, layout $layout at ${realWidth}dp")
                        .that(tile.height).isAtMost(tile.width + rendered.pixel)
                }
            }
        }
    }

    /**
     * For each of [realWidths], the layout a launcher picks for that width (the widest that fits) has tiles of the
     * width's columns, square when narrower than 120dp and at the card's ratio from 120dp. Expected values from the
     * issue: a cell is (width - 4) / columns, a tile 4dp narrower.
     */
    private fun assertSquareBelow120dp_cardFrom120dp(realWidths: IntProgression) {
        val layouts = WIDGET_SIZES.map { it.width }.sorted()
        for (realWidth in realWidths) {
            val layout = layouts.last { it.value <= realWidth }
            val rendered = widget.show(layout, realWidth.dp)
            if (realWidth in PNG_WIDTHS) rendered.savePng("layout-${layout.value.toInt()}-at-$realWidth")
            val columns = if (realWidth < 240) 1 else 2
            val tileWidth = (realWidth - 4f) / columns - 4f
            assertThat(rendered.tiles).hasSize(3)
            for (tile in rendered.tiles) {
                val where = "${tile.description}, layout $layout at ${realWidth}dp"
                // Each 2dp padding is a whole number of pixels (5px of 5.5 at 2.75) and the grid splits whole pixels
                // between its columns, so a tile can be a few pixels off the exact share.
                assertWithMessage(where).that(tile.width).isWithin(3 * rendered.pixel).of(tileWidth)
                // The sizer's picture is whole pixels too, and the ImageView rounds its height down.
                val height = if (tileWidth < 120f) tile.width else tile.width / CARD_ASPECT_RATIO
                assertWithMessage(where).that(tile.height).isWithin(2 * rendered.pixel).of(height)
            }
        }
    }

    private companion object {
        /** Widths around the shape's and the columns' switch points, drawn for a person to look at. */
        val PNG_WIDTHS = setOf(110, 127, 128, 160, 239, 240, 251, 252, 300)
    }
}
