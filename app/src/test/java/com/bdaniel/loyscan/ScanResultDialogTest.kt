package com.bdaniel.loyscan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanResultDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTheScannedContentAndItsFormat() {
        composeRule.setContent { ScanResultDialog(ScanResult("5901234123457", BarcodeFormat.EAN_13), onDismiss = {}) }

        composeRule.onNodeWithText("EAN-13").assertIsDisplayed()
        composeRule.onNodeWithText("5901234123457").assertIsDisplayed()
    }

    @Test
    fun aGs1Scan_saysSoNextToItsFormat() { // #41
        composeRule.setContent { ScanResultDialog(ScanResult("9799123456789012", BarcodeFormat.CODE_128, gs1 = true), onDismiss = {}) }

        composeRule.onNodeWithText("Code 128 · GS1").assertIsDisplayed()
    }

    @Test
    fun ok_dismissesIt() {
        var dismissed = 0
        composeRule.setContent { ScanResultDialog(ScanResult("42", BarcodeFormat.QR_CODE), onDismiss = { dismissed++ }) }

        composeRule.onNodeWithText("OK").performClick()

        assertThat(dismissed).isEqualTo(1)
    }
}
