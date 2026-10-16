package com.bdaniel.loyscan

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.bdaniel.loyscan.domain.LoyaltyCard
import com.bdaniel.loyscan.ui.toBrandColorOrNull
import com.bdaniel.loyscan.ui.theme.icons.Icons

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.ListScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    directions: ListDirections,
    viewModel: ListViewModel = hiltViewModel()
) {
    val cards by viewModel.cards.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Cards") },
                actions = {
                    IconButton(onClick = { /* TODO: Handle settings click */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = directions.toScanner) {
                Icon(Icons.Default.Add, contentDescription = "Add card")
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(innerPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cards) { card ->
                CardItem(
                    animatedVisibilityScope = animatedVisibilityScope,
                    card = card,
                    onClick = { directions.toCard(card.id, card.store.color) },
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.CardItem(
    animatedVisibilityScope: AnimatedVisibilityScope,
    card: LoyaltyCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColor = card.store.color.toBrandColorOrNull() ?: MaterialTheme.colorScheme.surfaceVariant
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardColor)
                .aspectRatio(1.5f)
                .sharedElement(
                    sharedContentState = rememberSharedContentState(key = "card/${card.id}"),
                    animatedVisibilityScope = animatedVisibilityScope
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!card.store.logo.isNullOrBlank()) {
                AsyncImage(
                    model = card.store.logo,
                    contentDescription = card.store.name,
                    modifier = Modifier
                        .fillMaxSize(0.6f)
                )
            } else {
                Text(
                    text = card.store.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (ColorUtils.calculateLuminance(cardColor.toArgb()) < 0.5) Color.White else Color.Black
                )
            }
        }
    }
}