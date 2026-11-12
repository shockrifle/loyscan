package com.bdaniel.loyscan.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** What the launcher is told about the widget (`res/xml/card_widget_info.xml`), read from the app's resources. */
@RunWith(AndroidJUnit4::class)
class CardWidgetInfoTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun theWidget_cantBeResizedBelowTheHeightOfOneTile() { // #108
        val minWidth = dimensionDp("minResizeWidth")
        val minHeight = dimensionDp("minResizeHeight")

        assertThat(minHeight).isEqualTo(90f)
        assertThat(minHeight).isAtLeast(widgetHeightForOneTile(minWidth.dp).value)
    }

    /** The provider's dimension attribute [name], in dp. */
    private fun dimensionDp(name: String): Float {
        val parser = context.resources.getXml(R.xml.card_widget_info)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "appwidget-provider") {
                val value = parser.getAttributeValue(ANDROID, name) ?: error("no $name")
                val match = Regex("""^([0-9.]+)(dp|dip)$""").find(value) ?: error("$name is $value, not in dp")
                return match.groupValues[1].toFloat()
            }
        }
        error("no appwidget-provider")
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
