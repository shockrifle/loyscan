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
}
