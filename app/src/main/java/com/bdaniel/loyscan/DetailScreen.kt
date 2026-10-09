package com.bdaniel.loyscan

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.bdaniel.loyscan.ui.hiltImageLoader
import com.bdaniel.loyscan.ui.toBrandColorOrNull
import com.bdaniel.loyscan.ui.theme.icons.Icons


@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.DetailScreen(
    color: String,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
    directions: DetailDirections,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val card by viewModel.card.collectAsState()
    val brandColor = color.toBrandColorOrNull() ?: MaterialTheme.colorScheme.surfaceVariant

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = card.brand.name) },
                navigationIcon = {
                    IconButton(onClick = { directions.toBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Handle edit */ }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = brandColor
                )
            )
        },
        modifier = Modifier.sharedElement(
            sharedContentState = rememberSharedContentState(key = "card/${card.id}"),
            animatedVisibilityScope = animatedVisibilityScope
        ),
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(brandColor),
                contentAlignment = Alignment.Center
            ) {
                if (card.brand.logo.isNotBlank()) {
                    AsyncImage(
                        model = card.brand.logo,
                        contentDescription = card.brand.name,
                        imageLoader = hiltImageLoader(),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                } else {
                    Text(
                        text = card.brand.name,
                        style = MaterialTheme.typography.headlineLarge,
                        color = if (ColorUtils.calculateLuminance(brandColor.toArgb()) < 0.5) Color.White else Color.Black,
                    )
                }
            }
            Card(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = card.name)
                    Text(text = card.brand.name)
                    Text(text = card.code)
                }
            }
        }
    }
}
