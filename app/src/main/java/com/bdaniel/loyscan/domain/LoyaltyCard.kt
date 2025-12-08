package com.bdaniel.loyscan.domain

data class LoyaltyCard(
    val id: Int,
    val name: String,
    val brand: Brand,
    val code: String,
    val barcodeFormat: String
) {
    companion object {
        val EMPTY = LoyaltyCard(0, "", Brand.EMPTY, "", "")
    }
}