package com.bdaniel.loyscan

import android.content.ClipData
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.bdaniel.loyscan.ui.barcode.Barcode
import com.bdaniel.loyscan.ui.barcode.BarcodeEncoder
import com.bdaniel.loyscan.ui.barcode.barcodeSize
import com.bdaniel.loyscan.ui.hiltImageLoader
import com.bdaniel.loyscan.ui.toBrandColorOrNull
import com.bdaniel.loyscan.ui.theme.icons.Icons
import kotlinx.coroutines.launch


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
                title = { Text(text = card.store.name) },
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
                if (!card.store.logo.isNullOrBlank()) {
                    AsyncImage(
                        model = card.store.logo,
                        contentDescription = card.store.name,
                        imageLoader = hiltImageLoader(),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                } else {
                    Text(
                        text = card.store.name,
                        style = MaterialTheme.typography.headlineLarge,
                        color = if (ColorUtils.calculateLuminance(brandColor.toArgb()) < 0.5) Color.White else Color.Black,
                    )
                }
            }
            Card(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    card.label?.let { Text(text = it, style = MaterialTheme.typography.titleMedium) }
                    Text(text = card.store.name)
                    val barcode = remember(card.barcodeContent, card.barcodeFormat) {
                        BarcodeEncoder.encode(card.barcodeContent, card.barcodeFormat)
                    }
                    if (barcode != null) {
                        Box(
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .fillMaxWidth()
                                // White whatever the theme, with a margin for the quiet zone: scanners need it.
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Barcode(barcode, Modifier.barcodeSize(barcode))
                        }
                    }
                    CopyableNumber(
                        number = card.displayNumber,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

/** The number in large type; a tap copies it, exactly as shown. */
@Composable
private fun CopyableNumber(number: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Text(
        text = number,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier.clickable(onClickLabel = "Copy number") {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Card number", number))) }
            // From Android 13 the system confirms a copy itself.
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
            }
        },
    )
}
