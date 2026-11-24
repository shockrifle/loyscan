package com.bdaniel.loyscan.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The widget's grid (#108): one column below 240dp of width, two from 240dp, card-ratio tiles. Expected sizes are worked
 * out by hand: the edge and the gap between tiles are 4dp (#182), made of 2dp around the grid and 2dp around every tile,
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
        assertThat(WIDGET_SIZES.map { it.height.value }.max()).isAtMost(90f) // the widget's minimum resize height
        assertThat(WIDGET_SIZES.map { it.width.value }.min()).isAtMost(110f) // its minimum resize width
    }

    @Test
    fun theLayoutChosenForAnyPhoneWidth_hasItsColumns_andTilesWithin10PercentOfTheCardRatio() {
        val widths = WIDGET_SIZES.map { it.width }.sorted()
        for (actual in 110..440) {
            val chosen = widths.last { it.value <= actual }
            val tileWidth = widgetTileSize(actual.dp).width.value // a tile fills its cell, whatever layout shows
            val tileHeight = widgetTileSize(chosen).height.value // its height comes from the layout's width

            assertWithMessage("columns at ${actual}dp").that(widgetColumns(chosen)).isEqualTo(widgetColumns(actual.dp))
            assertWithMessage("ratio at ${actual}dp").that(tileWidth / tileHeight).isWithin(0.10f * 85.60f / 53.98f).of(85.60f / 53.98f)
        }
    }

    @Test
    fun theHeightOfAWidgetHoldingOneTile_isTheTileAndThePadding() {
        // At 110dp: a 102dp tile, 64.32dp high, and the 4dp edge above and below.
        assertThat(widgetHeightForOneTile(110.dp).value).isWithin(0.01f).of(72.32f)
    }
}
