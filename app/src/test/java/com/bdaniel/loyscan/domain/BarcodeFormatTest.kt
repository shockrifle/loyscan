package com.bdaniel.loyscan.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BarcodeFormatTest {

    @Test
    fun forCustomStores_isEveryFormatWithoutTheGs1Mark_inNameOrder() { // #47
        assertThat(BarcodeFormat.forCustomStores).containsExactly(
            BarcodeFormat.AZTEC,
            BarcodeFormat.CODABAR,
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.CODE_128,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.EAN_8,
            BarcodeFormat.EAN_13,
            BarcodeFormat.ITF,
            BarcodeFormat.PDF_417,
            BarcodeFormat.QR_CODE,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
        ).inOrder()
        assertThat(BarcodeFormat.forCustomStores.none { it.isGs1 }).isTrue()
    }
}
