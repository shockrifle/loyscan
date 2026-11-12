package com.bdaniel.loyscan.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The widget's grid (#108): one column below 240dp of width, two from 240dp, card-ratio tiles. Expected sizes are worked
 * out by hand: the grid and every tile have 4dp of padding, so a cell is (width − 8) / columns and a tile 8dp narrower.
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
        val tile = widgetTileSize(300.dp) // cells of 146dp

        assertThat(tile.width.value).isWithin(0.01f).of(138f)
        assertThat(tile.height.value).isWithin(0.01f).of(87.02f) // 138 × 53.98 / 85.60
    }

    @Test
    fun aTile_hasTheCardsRatio_inOneColumn() {
        val tile = widgetTileSize(200.dp) // one cell of 192dp

        assertThat(tile.width.value).isWithin(0.01f).of(184f)
        assertThat(tile.height.value).isWithin(0.01f).of(116.03f)
    }

    @Test
    fun aWidgetWithNoSizeYet_hasNoTileSize_ratherThanANegativeOne() {
        assertThat(widgetTileSize(0.dp)).isEqualTo(DpSize(0.dp, 0.dp))
    }

    @Test
    fun aTilesCorners_areInProportionToAnId1Card() {
        // 6% of the short side: an ID-1 card has 3.18 mm corners on a 53.98 mm side.
        assertThat(widgetTileCornerRadius(DpSize(184.dp, 116.03.dp)).value).isWithin(0.01f).of(6.96f)
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
        // At 110dp: a 94dp tile, 59.28dp high, and 4dp of padding twice above and below.
        assertThat(widgetHeightForOneTile(110.dp).value).isWithin(0.01f).of(75.28f)
    }
}
