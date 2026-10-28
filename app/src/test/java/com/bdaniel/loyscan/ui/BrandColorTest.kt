package com.bdaniel.loyscan.ui

import androidx.compose.ui.graphics.Color
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
}
