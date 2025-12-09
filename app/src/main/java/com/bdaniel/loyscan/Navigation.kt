package com.bdaniel.loyscan

import androidx.compose.runtime.Immutable
import androidx.navigation.NavController
import kotlinx.serialization.Serializable

@Serializable
object List

@Immutable
data class ListDirections(
        val toCard: (id: Int) -> Unit,
        val toBack: () -> Unit
)

fun NavController.listDirections() = ListDirections(
        toCard = {
            navigate(Detail(it))
        },
        toBack = { navigateUp() }
)


@Serializable
data class Detail(val cardId: Int)

@Immutable
data class DetailDirections(
        val toBack: () -> Unit
)

fun NavController.detailDirections() = DetailDirections(
        toBack = { navigateUp() }
)