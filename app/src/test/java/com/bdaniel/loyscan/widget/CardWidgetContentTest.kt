package com.bdaniel.loyscan.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.layout.PaddingModifier
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import androidx.glance.unit.Dimension
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The widget's content (#11): one clickable tile per card, each showing the store's initials and the card's label. */
@RunWith(AndroidJUnit4::class)
class CardWidgetContentTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val tiles = listOf(
        WidgetTile("c1", "#112233", "TE", "Tesco", "Gold"),
        WidgetTile("c2", "#223344", "AL", "Aldi", "Family"),
    )

    @Test
    fun content_showsOneClickableTilePerCard_withItsInitialsAndLabel() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(300.dp, 200.dp))
        setContext(context)
        provideComposable { CardWidgetContent(tiles) }

        onNode(hasText("Gold")).assertExists()
        onNode(hasText("TE")).assertExists() // #108
        onNode(hasText("Family")).assertExists()
        onNode(hasText("AL")).assertExists()
        // One clickable tile per card. That a tile opens its own card (a start-activity intent carrying that card's
        // id) is checked on a device: the Glance unit API matches a start-activity action by component and
        // ActionParameters, not by the intent extras the tile uses, so it can't assert the id here (#11 field check).
        onAllNodes(hasClickAction()).assertCountEquals(2)
    }

    @Test
    fun anUnlabelledTile_showsOnlyItsInitials_neverTheStoreName() = runGlanceAppWidgetUnitTest { // #181, as #183
        setAppWidgetSize(DpSize(300.dp, 200.dp))
        setContext(context)
        provideComposable { CardWidgetContent(listOf(WidgetTile("c1", "#112233", "TE", "Tesco", label = null))) }

        onAllNodes(hasText("TE")).assertCountEquals(1)
        onAllNodes(hasText("Tesco")).assertCountEquals(0)
    }

    @Test
    fun aTile_isDescribedAsItsStoreAndLabel_orItsStoreAlone() = runGlanceAppWidgetUnitTest { // #181: TalkBack
        setAppWidgetSize(DpSize(300.dp, 200.dp))
        setContext(context)
        provideComposable {
            CardWidgetContent(listOf(tiles.first(), WidgetTile("c3", "#334455", "SP", "Spar", label = null)))
        }

        onAllNodes(hasClickAction() and hasContentDescriptionEqualTo("Tesco, Gold")).assertCountEquals(1)
        onAllNodes(hasClickAction() and hasContentDescriptionEqualTo("Spar")).assertCountEquals(1)
    }

    @Test
    fun content_inANarrowWidget_stillShowsEveryTile() = runGlanceAppWidgetUnitTest { // #108: one column
        setAppWidgetSize(DpSize(110.dp, 90.dp))
        setContext(context)
        provideComposable { CardWidgetContent(tiles) }

        onAllNodes(hasClickAction()).assertCountEquals(2)
    }

    @Test
    fun content_withNoCards_showsNoTiles() = runGlanceAppWidgetUnitTest {
        setContext(context)
        provideComposable { CardWidgetContent(emptyList()) }

        onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    @Config(sdk = [32]) // #182: Android 12+, where the widget rounds its background
    fun content_roundsTheBackground_andEachTileConcentricWithIt() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(300.dp, 200.dp))
        setContext(context)
        provideComposable { CardWidgetContent(tiles) }

        // The widget rounds its own background, so the launcher leaves it alone; a tile's corners are 4dp less.
        onAllNodes(hasCornerRadius(12.dp)).assertCountEquals(1)
        onAllNodes(hasClickAction() and hasCornerRadius(8.dp)).assertCountEquals(2)
    }

    @Test
    @Config(sdk = [29]) // #182: before Android 12 the widget's background is square, and so are its tiles
    fun content_beforeAndroid12_leavesTheTilesSquare() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(300.dp, 200.dp))
        setContext(context)
        provideComposable { CardWidgetContent(tiles) }

        onAllNodes(hasClickAction() and hasCornerRadius(0.dp)).assertCountEquals(2)
        onAllNodes(hasCornerRadius(12.dp)).assertCountEquals(0)
    }

    @Test
    fun content_hasA4dpEdgeAndGap_madeOf2dpAroundTheGridAndAroundEachTile() = runGlanceAppWidgetUnitTest { // #182
        setAppWidgetSize(DpSize(300.dp, 200.dp))
        setContext(context)
        provideComposable { CardWidgetContent(tiles) }

        // Glance has no grid spacing: the grid's 2dp and a tile's 2dp make the edge, two tiles' 2dp the gap.
        onAllNodes(hasPaddingOnEverySide(2.dp)).assertCountEquals(1 + tiles.size)
    }

    // Glance's `CornerRadiusModifier` is internal to Kotlin, so its radius is read by reflection.
    private fun hasCornerRadius(radius: androidx.compose.ui.unit.Dp) =
        GlanceNodeMatcher<MappedNode>("has corner radius $radius") { node ->
            node.value.emittable.modifier.elements()
                .filter { it.javaClass.name == "androidx.glance.appwidget.CornerRadiusModifier" }
                .any { (it.javaClass.getMethod("getRadius").invoke(it) as? Dimension.Dp)?.dp == radius }
        }

    private fun hasPaddingOnEverySide(padding: androidx.compose.ui.unit.Dp) =
        GlanceNodeMatcher<MappedNode>("has $padding padding on every side") { node ->
            val paddings = node.value.emittable.modifier.elements().filterIsInstance<PaddingModifier>()
            paddings.isNotEmpty() && paddings.reduce(PaddingModifier::plus).let {
                val start = it.left + it.start
                val end = it.right + it.end
                listOf(start, it.top, end, it.bottom).all { side -> side.dp == padding && side.resourceIds.isEmpty() }
            }
        }

    private fun GlanceModifier.elements(): List<GlanceModifier.Element> = foldIn(emptyList()) { acc, e -> acc + e }
}
