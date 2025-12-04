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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.DetailScreen(
        animatedVisibilityScope: AnimatedVisibilityScope,
        item: String,
        modifier: Modifier = Modifier
) {
    Card(
            modifier = modifier
                    .fillMaxSize()
                    .sharedElement(
                            sharedContentState = rememberSharedContentState(key = "item/$item"),
                            animatedVisibilityScope = animatedVisibilityScope
                    )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Details for $item")
        }
    }
}