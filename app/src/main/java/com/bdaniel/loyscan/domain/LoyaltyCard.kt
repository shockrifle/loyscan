package com.bdaniel.loyscan.domain

data class LoyaltyCard(
    val id: Int,
    val name: String,
    val brand: String,
    val cardNumber: String,
    val barcodeType: String
)