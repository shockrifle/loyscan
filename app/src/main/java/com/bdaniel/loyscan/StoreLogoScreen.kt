package com.bdaniel.loyscan

import com.bdaniel.loyscan.ui.toBrandColorOrNull
import com.bdaniel.loyscan.domain.LogoTreatment
import com.bdaniel.loyscan.domain.LogoStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.bdaniel.loyscan.ui.CARD_ASPECT_RATIO
import com.bdaniel.loyscan.ui.CardPhoto
import com.bdaniel.loyscan.ui.LogoSearch
import com.bdaniel.loyscan.ui.PhotoCropScreen
import com.bdaniel.loyscan.ui.hiltImageLoader
import com.bdaniel.loyscan.ui.rememberLogoSearch
import com.bdaniel.loyscan.ui.scanner.PicturePicker
import com.bdaniel.loyscan.ui.scanner.rememberPicturePicker
import com.bdaniel.loyscan.ui.LogoPreview
import com.bdaniel.loyscan.ui.theme.CardShape
import com.bdaniel.loyscan.ui.theme.Spacing
import com.bdaniel.loyscan.ui.theme.icons.Icons

/**
 * A store's logo (#151), reached from the store picker or a custom store's edit form: a preview of the store's tile,
 * "Choose image" (the system photo picker, then a square crop that can zoom out so a wide logo fits whole), "Find a
 * logo online" (a web image search in the browser) and, with a logo, "Remove logo". The [picker] and [logoSearch] are
 * parameters, fakes in tests.
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
    var choosingCard by rememberSaveable { mutableStateOf(false) }
    // Its list emptied (the cards deleted or their photos removed meanwhile): nothing is left to ask about. Not before
    // the list has loaded, or a question restored after process death would close before its cards come (#151 review).
    val noCardPhotos = state.cardPhotosLoaded && state.cardPhotos.isEmpty()
    LaunchedEffect(noCardPhotos) { if (noCardPhotos) choosingCard = false }

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
                LogoPreview(name = store.name, color = store.color, logo = store.logo, logoTreatment = store.logoTreatment)
                if (store.logo != null) {
                    LogoStyleControls(
                        treatment = store.logoTreatment,
                        enabled = state.canChange,
                        onStyle = viewModel::onLogoStyle,
                        onTint = viewModel::onLogoTint,
                    )
                }
                if (state.styleFailed) {
                    Text(
                        stringResource(R.string.logo_style_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
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
                if (state.cardPhotos.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            state.cardPhotos.singleOrNull()?.let { viewModel.onCardPhoto(it.photo) } ?: run { choosingCard = true }
                        },
                        enabled = state.canChange,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.use_card_photo), textAlign = TextAlign.Center)
                    }
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
        if (choosingCard && state.cardPhotos.isNotEmpty()) {
            CardPhotoChoiceDialog(
                choices = state.cardPhotos,
                onChoose = { choosingCard = false; viewModel.onCardPhoto(it.photo) },
                onDismiss = { choosingCard = false },
            )
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

/**
 * How the store's logo is drawn (#240), under the preview while it has one: As is, Single colour or Recolour, and unless As is,
 * the colour: white, black, then the custom store palette ([LOGO_COLOURS]). Each tap is saved at once; TalkBack reads the
 * styles and the colours by name as choices, the chosen one selected.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogoStyleControls(
    treatment: LogoTreatment,
    enabled: Boolean,
    onStyle: (LogoStyle?) -> Unit,
    onTint: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(stringResource(R.string.logo_style), style = MaterialTheme.typography.titleSmall)
        val styles = listOf(
            null to R.string.logo_style_as_is,
            LogoStyle.SINGLE_COLOUR to R.string.logo_style_single_colour,
            LogoStyle.RECOLOUR to R.string.logo_style_recolour,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            styles.forEachIndexed { index, (style, label) ->
                SegmentedButton(
                    selected = treatment.style == style,
                    onClick = { onStyle(style) },
                    shape = SegmentedButtonDefaults.itemShape(index, styles.size),
                    enabled = enabled,
                ) {
                    Text(stringResource(label), textAlign = TextAlign.Center)
                }
            }
        }
        if (treatment.style != null) {
            Text(stringResource(R.string.logo_colour), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                LOGO_COLOURS.forEach { swatch ->
                    val selected = swatch.hex.equals(treatment.tint, ignoreCase = true)
                    val swatchName = stringResource(swatch.name)
                    Box(
                        Modifier
                            .size(SWATCH_SIZE)
                            .clip(CircleShape)
                            .background(swatch.hex.toBrandColorOrNull() ?: MaterialTheme.colorScheme.surfaceVariant)
                            // Every swatch is outlined, so white shows on a light screen; the chosen one more boldly.
                            .border(
                                if (selected) SWATCH_SELECTED_BORDER else SWATCH_BORDER,
                                if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            )
                            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) { onTint(swatch.hex) }
                            .semantics { contentDescription = swatchName },
                    )
                }
            }
        }
    }
}

/** The colours a logo can be drawn in (#240): white, black, then the custom store palette. */
val LOGO_COLOURS: List<CustomStoreViewModel.Swatch> = listOf(
    CustomStoreViewModel.Swatch(R.string.colour_white, "#FFFFFF"),
    CustomStoreViewModel.Swatch(R.string.colour_black, "#000000"),
) + CustomStoreViewModel.COLORS

/** A colour swatch, large enough to tap; as the custom store form's. */
private val SWATCH_SIZE = 40.dp
private val SWATCH_BORDER = 1.dp
private val SWATCH_SELECTED_BORDER = 3.dp

/** "Which card's photo?": the front photos of the store's cards, each with its label or number; the one tapped is chosen. */
@Composable
private fun CardPhotoChoiceDialog(
    choices: List<CardPhotoChoice>,
    onChoose: (CardPhotoChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.which_card_photo)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                choices.forEach { choice ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(CardShape)
                            .clickable(role = Role.Button) { onChoose(choice) }
                            .padding(vertical = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        AsyncImage(
                            model = CardPhoto(choice.photo),
                            contentDescription = null,
                            imageLoader = hiltImageLoader(),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(CARD_PHOTO_CHOICE_WIDTH).aspectRatio(CARD_ASPECT_RATIO).clip(CardShape),
                        )
                        Text(choice.title, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private val CARD_PHOTO_CHOICE_WIDTH = 72.dp
