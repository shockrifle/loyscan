package com.bdaniel.loyscan

import androidx.compose.runtime.Immutable
import androidx.navigation.NavController
import kotlinx.serialization.Serializable

@Serializable
object List

@Immutable
data class ListDirections(
    val toCard: (id: String, color: String) -> Unit,
    val toBack: () -> Unit
)

fun NavController.listDirections() = ListDirections(
    toCard = { id, color ->
        navigate(Detail(id, color))
    },
    toBack = { navigateUp() }
)


@Serializable
data class Detail(val cardId: String, val color: String)

@Immutable
data class DetailDirections(
    val toBack: () -> Unit
)

fun NavController.detailDirections() = DetailDirections(
    toBack = { navigateUp() }
)