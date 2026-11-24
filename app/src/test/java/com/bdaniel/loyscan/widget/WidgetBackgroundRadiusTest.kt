package com.bdaniel.loyscan.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The widget background's corner radius the tiles nest in (#182): the platform's `system_app_widget_background_radius`
 * from Android 12, none before. 16dp is the AOSP value; a launcher or a device maker can set another one. The density is
 * xxhdpi so that a radius read in pixels instead of dp would be 48, not 16.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [32], qualifiers = "xxhdpi")
class WidgetBackgroundRadiusTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun fromAndroid12_itIsThePlatformsWidgetBackgroundRadius_inDp() {
        assertThat(widgetBackgroundRadius(context, sdkInt = 31)).isEqualTo(16.dp)
        assertThat(widgetBackgroundRadius(context, sdkInt = 32)).isEqualTo(16.dp)
    }

    @Test
    fun beforeAndroid12_thereIsNone() {
        assertThat(widgetBackgroundRadius(context, sdkInt = 30)).isNull()
        assertThat(widgetBackgroundRadius(context, sdkInt = 29)).isNull()
    }
}
