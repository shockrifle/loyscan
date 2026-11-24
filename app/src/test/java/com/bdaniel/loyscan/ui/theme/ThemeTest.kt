package com.bdaniel.loyscan.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The design values (#108). These pin the agreed values: Material 3's default shapes are the same numbers, so the
 * shapes test went red only as a compile error before `LoyScanShapes` existed.
 */
@RunWith(AndroidJUnit4::class)
class ThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theTheme_providesTheAgreedShapes() {
        var shapes: Shapes? = null
        composeRule.setContent { LoyScanTheme { shapes = MaterialTheme.shapes } }

        assertThat(shapes).isEqualTo(LoyScanShapes)
        assertThat(LoyScanShapes.extraSmall).isEqualTo(RoundedCornerShape(4.dp))
        assertThat(LoyScanShapes.small).isEqualTo(RoundedCornerShape(8.dp))
        assertThat(LoyScanShapes.medium).isEqualTo(RoundedCornerShape(12.dp))
        assertThat(LoyScanShapes.large).isEqualTo(RoundedCornerShape(16.dp))
        assertThat(LoyScanShapes.extraLarge).isEqualTo(RoundedCornerShape(28.dp))
    }

    @Test
    fun spacing_isTheAgreedScale() {
        assertThat(listOf(Spacing.xs, Spacing.s, Spacing.m, Spacing.l, Spacing.xl, Spacing.xxl))
            .containsExactly(4.dp, 8.dp, 12.dp, 16.dp, 24.dp, 32.dp).inOrder()
    }

    @Test
    fun theWidgetsCorners_are12dp() { // #182: the widget's own radius, not the platform's (28dp on some devices)
        assertThat(WIDGET_CORNER_RADIUS).isEqualTo(12.dp)
    }

    @Test
    fun theCardShape_hasCornersInProportionToAnId1Card() {
        // 3.18 mm corners on a 53.98 mm short side.
        assertThat(CardShape).isEqualTo(RoundedCornerShape(percent = 6))
        assertThat(CARD_CORNER_FRACTION).isWithin(0.002f).of(3.18f / 53.98f)
    }
}
