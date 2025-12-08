package com.bdaniel.loyscan

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.DetailScreen(
        animatedVisibilityScope: AnimatedVisibilityScope,
        modifier: Modifier = Modifier,
        viewModel: DetailViewModel = hiltViewModel()
) {
    val card by viewModel.card.collectAsState()

    Card(
            modifier = modifier
                    .fillMaxSize()
                    .sharedElement(
                            sharedContentState = rememberSharedContentState(key = "item/${card?.id}"),
                            animatedVisibilityScope = animatedVisibilityScope
                    )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            card.let {
                Text(text = it.name)
                Text(text = it.brand.name)
                Text(text = it.code)
            }
        }
    }
}