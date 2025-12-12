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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.EventListener
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import com.bdaniel.loyscan.ui.theme.icons.Icons
import timber.log.Timber


@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.DetailScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
    directions: DetailDirections,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val card by viewModel.card.collectAsState()

    val color = if (card.brand.color.isNotEmpty()) {
        Color(card.brand.color.toColorInt())
    } else {
        Color.Transparent
    }

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
                    containerColor = color
                )
            )
        }
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
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                if (card.brand.logo.isNotBlank()) {
                    val imageLoader = coil.ImageLoader.Builder(LocalContext.current)
                        .components {
                            add(SvgDecoder.Factory())
                        }
                        .eventListener(object : EventListener {
                            override fun onError(request: coil.request.ImageRequest, result: coil.request.ErrorResult) {
                                Timber.e(result.throwable, "Image loading failed for: ${request.data}")
                            }
                        })
                        .build()
                    AsyncImage(
                        model = card.brand.logo,
                        contentDescription = card.brand.name,
                        imageLoader = imageLoader,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                }
            }
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .sharedElement(
                        sharedContentState = rememberSharedContentState(key = "item/${card.id}"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
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
