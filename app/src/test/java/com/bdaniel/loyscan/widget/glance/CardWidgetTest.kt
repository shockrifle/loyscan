package com.bdaniel.loyscan.widget.glance

import androidx.glance.appwidget.SizeMode
import com.bdaniel.loyscan.widget.WIDGET_SIZES
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CardWidgetTest {

    @Test
    fun theWidget_isLaidOutForEachOfItsSizes_andTheLauncherPicksOne() { // #108, #216: the render tests assume it
        val sizeMode = CardWidget().sizeMode

        assertThat(sizeMode).isInstanceOf(SizeMode.Responsive::class.java)
        assertThat((sizeMode as SizeMode.Responsive).sizes).isEqualTo(WIDGET_SIZES)
    }
}
