package com.bdaniel.loyscan.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The widget's grid (#108): one column below 240dp of width, two from 240dp, card-ratio tiles, square below 120dp of
 * tile width (#216). Expected sizes are worked out by hand: the edge and the gap between tiles are 4dp (#182), made of 2dp around the grid and 2dp around every tile,
 * so a cell is (width − 4) / columns and a tile 4dp narrower.
 */
class WidgetLayoutTest {

    @Test
    fun aWidgetNarrowerThan240dp_hasOneColumn() {
        assertThat(widgetColumns(110.dp)).isEqualTo(1)
        assertThat(widgetColumns(239.dp)).isEqualTo(1)
    }

    @Test
    fun aWidgetFrom240dp_hasTwoColumns() {
        assertThat(widgetColumns(240.dp)).isEqualTo(2)
        assertThat(widgetColumns(400.dp)).isEqualTo(2)
    }

    @Test
    fun aTile_hasTheCardsRatio_inTwoColumns() {
        val tile = widgetTileSize(300.dp) // cells of 148dp

        assertThat(tile.width.value).isWithin(0.01f).of(144f)
        assertThat(tile.height.value).isWithin(0.01f).of(90.81f) // 144 × 53.98 / 85.60
    }

    @Test
    fun aTile_hasTheCardsRatio_inOneColumn() {
        val tile = widgetTileSize(200.dp) // one cell of 196dp

        assertThat(tile.width.value).isWithin(0.01f).of(192f)
        assertThat(tile.height.value).isWithin(0.01f).of(121.08f)
    }

    @Test
    fun aTileNarrowerThan120dp_isSquare_inOneColumn() { // #216
        assertThat(widgetTileShape(110.dp)).isEqualTo(WidgetTileShape.SQUARE)
        assertThat(widgetTileSize(110.dp)).isEqualTo(DpSize(102.dp, 102.dp)) // one cell of 106dp
        assertThat(widgetTileShape(127.9.dp)).isEqualTo(WidgetTileShape.SQUARE) // a 119.9dp tile
    }

    @Test
    fun aTileNarrowerThan120dp_isSquare_inTwoColumns() { // #216
        assertThat(widgetTileShape(240.dp)).isEqualTo(WidgetTileShape.SQUARE)
        assertThat(widgetTileSize(240.dp)).isEqualTo(DpSize(114.dp, 114.dp)) // cells of 118dp
        assertThat(widgetTileShape(251.9.dp)).isEqualTo(WidgetTileShape.SQUARE) // 119.95dp tiles
    }

    @Test
    fun aTileFrom120dp_hasTheCardsRatio() { // #216
        assertThat(widgetTileShape(128.dp)).isEqualTo(WidgetTileShape.CARD) // one cell of 124dp, a 120dp tile
        assertThat(widgetTileSize(128.dp).height.value).isWithin(0.01f).of(75.67f) // 120 x 53.98 / 85.60
        assertThat(widgetTileShape(239.dp)).isEqualTo(WidgetTileShape.CARD)
        assertThat(widgetTileShape(252.dp)).isEqualTo(WidgetTileShape.CARD) // cells of 124dp, 120dp tiles
    }

    @Test
    fun aWidgetWithNoSizeYet_hasNoTileSize_ratherThanANegativeOne() {
        assertThat(widgetTileSize(0.dp)).isEqualTo(DpSize(0.dp, 0.dp))
    }

    @Test
    fun theEdgeAndTheGapBetweenTiles_are4dp() { // #182, was 8dp
        assertThat(WIDGET_SPACING).isEqualTo(4.dp)
    }

    @Test
    fun fromAndroid12_theBackgroundIsRoundedWithTheWidgetsOwnRadius() { // #182
        assertThat(widgetBackgroundRadius(sdkInt = 31)).isEqualTo(12.dp)
        assertThat(widgetBackgroundRadius(sdkInt = 35)).isEqualTo(12.dp)
    }

    @Test
    fun beforeAndroid12_theBackgroundHasNoRadius() {
        assertThat(widgetBackgroundRadius(sdkInt = 30)).isNull()
        assertThat(widgetBackgroundRadius(sdkInt = 29)).isNull()
    }

    @Test
    fun aTilesCorners_nestInTheWidgetBackgroundsCorners() { // #182
        // The background's radius less the 4dp between the widget's edge and the tile.
        assertThat(widgetTileCornerRadius(16.dp)).isEqualTo(12.dp)
        assertThat(widgetTileCornerRadius(28.dp)).isEqualTo(24.dp)
    }

    @Test
    fun aBackgroundRadiusSmallerThanTheInset_givesSquareTiles_notANegativeRadius() {
        assertThat(widgetTileCornerRadius(2.dp)).isEqualTo(0.dp)
    }

    @Test
    fun withNoBackgroundRadius_beforeAndroid12_theTilesAreSquare() {
        assertThat(widgetTileCornerRadius(null)).isEqualTo(0.dp)
    }

    @Test
    fun theLayoutSizes_fitEveryWidgetHeight_soTheWidthAloneChoosesOne() {
        // Android 12+ shows the largest given layout that fits the widget's real size (#108: on a device the launcher
        // reported sizes that didn't fit, and the landscape layout showed squeezed into the portrait widget).
        assertThat(WIDGET_SIZES.size).isAtMost(16) // RemoteViews takes at most 16 sized layouts
        assertThat(WIDGET_SIZES.map { it.height.value }.max()).isAtMost(110f) // the widget's minimum resize height (#216)
        assertThat(WIDGET_SIZES.map { it.width.value }.min()).isAtMost(110f) // its minimum resize width
    }

    @Test
    fun theLayoutChosenForAnyWidth_hasThatWidthsColumnsAndTileShape() { // #216
        // From Android 12 a launcher that picks the layout for the widget's width shows the widest that fits.
        val layouts = WIDGET_SIZES.map { it.width }.sorted()
        for (tenths in 1100..4800) {
            val width = tenths / 10f
            val layout = layouts.last { it.value <= width }
            val columns = if (width < 240f) 1 else 2
            val tileWidth = (width - 4f) / columns - 4f
            val shape = if (tileWidth < 120f) WidgetTileShape.SQUARE else WidgetTileShape.CARD

            assertWithMessage("columns at ${width}dp").that(widgetColumns(layout)).isEqualTo(columns)
            assertWithMessage("shape at ${width}dp").that(widgetTileShape(layout)).isEqualTo(shape)
        }
    }

    @Test
    fun theHeightOfAWidgetHoldingOneTile_isTheTileAndThePadding() {
        // At 110dp: a square 102dp tile (#216), and the 4dp edge above and below.
        assertThat(widgetHeightForOneTile(110.dp).value).isWithin(0.01f).of(110f)
    }

    @Test
    fun theLogoBox_isA216dpCardTileLessAn8dpMargin() { // #181
        // The widest tile of the layouts before #216, one column at 224dp: a cell of 220dp, a tile of 216 × 136.21dp
        // (216 / 1.5858). Less 8dp on every side: 200 × 120.21dp.
        val box = widgetLogoBox()

        assertThat(box.width.value).isWithin(0.01f).of(200f)
        assertThat(box.height.value).isWithin(0.01f).of(120.21f)
    }

    @Test
    fun theLogoBoxInPixels_isItsDpTimesTheDensity_roundedDown() { // #181: 200 × 2.75 = 550, 120.21 × 2.75 = 330.6
        assertThat(widgetLogoBoxPx(density = 2.75f)).isEqualTo(IntSize(550, 330))
        assertThat(widgetLogoBoxPx(density = 1f)).isEqualTo(IntSize(200, 120))
    }

    @Test
    fun thePhotoBox_isA216dpCardTile() { // #185: a photo fills the tile, no margin
        // The logo box's tile, 216 × 136.21dp (216 / 1.5858), whole.
        val box = widgetPhotoBox()

        assertThat(box.width.value).isWithin(0.01f).of(216f)
        assertThat(box.height.value).isWithin(0.01f).of(136.21f)
    }

    @Test
    fun thePhotoBoxInPixels_isItsDpTimesTheDensity_roundedDown() { // #185: 216 × 2.75 = 594, 136.21 × 2.75 = 374.58
        assertThat(widgetPhotoBoxPx(density = 2.75f)).isEqualTo(IntSize(594, 374))
        assertThat(widgetPhotoBoxPx(density = 1f)).isEqualTo(IntSize(216, 136))
    }
}
