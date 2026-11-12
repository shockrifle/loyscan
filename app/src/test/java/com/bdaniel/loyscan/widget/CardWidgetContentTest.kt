package com.bdaniel.loyscan.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** The widget's content (#11): one clickable tile per card, each showing the store's initials and the card's label. */
@RunWith(AndroidJUnit4::class)
class CardWidgetContentTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val tiles = listOf(
        WidgetTile("c1", "#112233", "TE", "Tesco"),
        WidgetTile("c2", "#223344", "AL", "Aldi"),
    )

    @Test
    fun content_showsOneClickableTilePerCard_withItsInitialsAndLabel() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(300.dp, 200.dp))
        setContext(context)
        provideComposable { CardWidgetContent(tiles) }

        onNode(hasText("Tesco")).assertExists()
        onNode(hasText("TE")).assertExists() // #108
        onNode(hasText("Aldi")).assertExists()
        onNode(hasText("AL")).assertExists()
        // One clickable tile per card. That a tile opens its own card (a start-activity intent carrying that card's
        // id) is checked on a device: the Glance unit API matches a start-activity action by component and
        // ActionParameters, not by the intent extras the tile uses, so it can't assert the id here (#11 field check).
        onAllNodes(hasClickAction()).assertCountEquals(2)
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
}
