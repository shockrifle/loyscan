package com.bdaniel.loyscan.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.R
import com.bdaniel.loyscan.widget.glance.CardWidgetReceiver
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

        assertThat(minHeight).isEqualTo(110f) // #216: a square 102dp tile and its 4dp edges
        assertThat(minHeight).isAtLeast(widgetHeightForOneTile(minWidth.dp).value)
    }

    @Test
    fun theWidget_isOfferedToTheLauncher() { // #178: the Play listing promises a home-screen widget
        val receivers = context.packageManager.queryBroadcastReceivers(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(context.packageName),
            PackageManager.GET_META_DATA,
        )

        val widget = receivers.single().activityInfo
        assertThat(widget.name).isEqualTo(CardWidgetReceiver::class.java.name)
        assertThat(widget.metaData.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER)).isEqualTo(R.xml.card_widget_info)
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
