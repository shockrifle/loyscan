package com.bdaniel.loyscan.domain

data class Brand(
    val id: Int,
    val name: String,
    val color: String,
    val logo: String
) {
    companion object {
        val EMPTY = Brand(0, "", "", "")
    }
}