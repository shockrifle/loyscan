package com.bdaniel.loyscan

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.bdaniel.loyscan.domain.Store
import com.bdaniel.loyscan.ui.CARD_ASPECT_RATIO
import com.bdaniel.loyscan.ui.CardPhoto
import com.bdaniel.loyscan.ui.LogoSearch
import com.bdaniel.loyscan.ui.PhotoCropScreen
import com.bdaniel.loyscan.ui.contrastingTextColor
import com.bdaniel.loyscan.ui.hiltImageLoader
import com.bdaniel.loyscan.ui.rememberLogoSearch
import com.bdaniel.loyscan.ui.scanner.PicturePicker
import com.bdaniel.loyscan.ui.scanner.rememberPicturePicker
import com.bdaniel.loyscan.ui.theme.CardShape
import com.bdaniel.loyscan.ui.theme.Spacing
import com.bdaniel.loyscan.ui.theme.icons.Icons
import com.bdaniel.loyscan.ui.toBrandColorOrNull

/** The logo screen's preview: the store as a card tile, its logo or its name on the brand colour. */
const val STORE_LOGO_PREVIEW_TAG = "storeLogoPreview"

/** The logo in the preview, when the store has one that loads. */
const val STORE_LOGO_PREVIEW_IMAGE_TAG = "storeLogoPreviewImage"

/** How much of the preview the logo takes, as on a card's tile. */
private const val LOGO_FRACTION = 0.6f

/**
 * A store's logo (#151), reached from the store picker: a preview of the store's tile, "Choose image" (the system
 * photo picker, then a square crop that can zoom out so a wide logo fits whole), "Find a logo online" (a web image
 * search in the browser) and, with a logo, "Remove logo". The [picker] and [logoSearch] are parameters, fakes in tests.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreLogoScreen(
    directions: StoreLogoDirections,
    viewModel: StoreLogoViewModel = hiltViewModel(),
    picker: @Composable (onPicked: (Uri?) -> Unit) -> PicturePicker = { rememberPicturePicker(it) },
    logoSearch: LogoSearch = rememberLogoSearch(),
) {
    val state by viewModel.state.collectAsState()
    val pick = picker(viewModel::onPicked)

    Box {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.store_logo)) },
                    navigationIcon = {
                        IconButton(onClick = directions.toBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                )
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            if (state.storeGone) {
                Text(stringResource(R.string.store_gone), modifier = Modifier.padding(innerPadding).padding(Spacing.l))
            }
            val store = state.store ?: return@Scaffold
            Column(
                Modifier.padding(innerPadding).verticalScroll(rememberScrollState()).padding(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                LogoPreview(store)
                Text(stringResource(R.string.store_logo_hint), style = MaterialTheme.typography.bodyMedium)
                if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.failed) {
                    Text(
                        stringResource(R.string.logo_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(onClick = pick::pick, enabled = state.canChange, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.choose_image), textAlign = TextAlign.Center)
                }
                OutlinedButton(onClick = { logoSearch.open(store.name) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.find_logo_online), textAlign = TextAlign.Center)
                }
                if (store.logo != null) {
                    TextButton(onClick = viewModel::removeLogo, enabled = state.canChange, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.remove_logo), textAlign = TextAlign.Center)
                    }
                }
            }
        }
        state.cropping?.let { cropping ->
            PhotoCropScreen(
                photo = cropping,
                description = stringResource(R.string.logo_picture),
                onConfirm = viewModel::confirmCrop,
                onCancel = viewModel::cancelCrop,
                aspectRatio = 1f,
                zoomOutToFit = true,
                useWholeLabel = stringResource(R.string.use_whole_image),
            )
        }
    }
}

/** The store as its cards' tiles show it: the logo on the brand colour, or the name when it has none or it can't load. */
@Composable
private fun LogoPreview(store: Store) {
    val color = store.color.toBrandColorOrNull() ?: MaterialTheme.colorScheme.surfaceVariant
    var logoFailed by remember(store.logo) { mutableStateOf(false) }
    val logo = store.logo?.takeUnless { logoFailed }
    val description = stringResource(R.string.logo_of_store, store.name)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(CARD_ASPECT_RATIO)
            .clip(CardShape)
            .background(color)
            .testTag(STORE_LOGO_PREVIEW_TAG),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            AsyncImage(
                model = CardPhoto(logo),
                contentDescription = null,
                imageLoader = hiltImageLoader(),
                onError = { logoFailed = true },
                modifier = Modifier
                    .fillMaxSize(LOGO_FRACTION)
                    .semantics { contentDescription = description }
                    .testTag(STORE_LOGO_PREVIEW_IMAGE_TAG),
            )
        } else {
            Text(
                store.name,
                style = MaterialTheme.typography.headlineLarge,
                color = color.contrastingTextColor(),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(Spacing.l),
            )
        }
    }
}
