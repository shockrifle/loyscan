package com.bdaniel.loyscan.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BrandColorTest {

    @Test
    fun textOnADarkColour_isWhite() {
        assertThat(Color(0xFF00539F).contrastingTextColor()).isEqualTo(Color.White)
    }

    @Test
    fun textOnALightColour_isBlack() {
        assertThat(Color(0xFFFFD700).contrastingTextColor()).isEqualTo(Color.Black)
    }

    @Test
    fun textOnAMidGrey_isBlack_whichContrastsMoreThanWhite() {
        // Luminance ≈ 0.18: contrast 4.7:1 with black, 4.5:1 with white.
        assertThat(Color(0xFF777777).contrastingTextColor()).isEqualTo(Color.Black)
    }

    @Test
    fun whiteText_onThePhotoLabelScrim_readsOnAWhitePhoto() { // review of #10: a gradient left it at about 2.5:1
        val behindTheText = PHOTO_LABEL_SCRIM.compositeOver(Color.White)

        // WCAG AA for normal text.
        assertThat(1.05f / (behindTheText.luminance() + 0.05f)).isAtLeast(4.5f)
    }
}
