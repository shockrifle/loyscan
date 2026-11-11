package com.bdaniel.loyscan.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.domain.BarcodeFormat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class BarcodeFormatNameTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inEnglish_namesEachFormatAsItIsCalled() {
        show(BarcodeFormat.QR_CODE, BarcodeFormat.GS1_QR_CODE, BarcodeFormat.EAN_13)

        composeRule.onNodeWithText("QR code").assertExists()
        composeRule.onNodeWithText("GS1 QR code").assertExists()
        composeRule.onNodeWithText("EAN-13").assertExists()
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_translatesTheQrNames_andKeepsTheOthers() { // #72: other symbology names are proper names
        show(BarcodeFormat.QR_CODE, BarcodeFormat.GS1_QR_CODE, BarcodeFormat.EAN_13, BarcodeFormat.DATA_MATRIX)

        composeRule.onNodeWithText("QR-kód").assertExists()
        composeRule.onNodeWithText("GS1 QR-kód").assertExists()
        composeRule.onNodeWithText("EAN-13").assertExists()
        composeRule.onNodeWithText("Data Matrix").assertExists()
    }

    private fun show(vararg formats: BarcodeFormat) {
        composeRule.setContent { formats.forEach { Text(barcodeFormatName(it)) } }
    }
}
