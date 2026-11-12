package com.bdaniel.loyscan

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import android.content.ClipData
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.bdaniel.loyscan.ui.theme.CardShape
import com.bdaniel.loyscan.ui.theme.Spacing
import com.bdaniel.loyscan.domain.LoyaltyCard
import com.bdaniel.loyscan.ui.CARD_ASPECT_RATIO
import com.bdaniel.loyscan.ui.CardPhoto
import com.bdaniel.loyscan.ui.FullBrightness
import com.bdaniel.loyscan.ui.KeepScreenOn
import com.bdaniel.loyscan.ui.barcode.Barcode
import com.bdaniel.loyscan.ui.barcode.BarcodeEncoder
import com.bdaniel.loyscan.ui.barcode.EncodedBarcode
import com.bdaniel.loyscan.ui.barcode.barcodeSize
import com.bdaniel.loyscan.ui.barcodeFormatName
import com.bdaniel.loyscan.ui.hiltImageLoader
import com.bdaniel.loyscan.ui.contrastingTextColor
import com.bdaniel.loyscan.ui.toBrandColorOrNull
import com.bdaniel.loyscan.ui.theme.icons.Icons
import kotlinx.coroutines.launch

const val PHOTO_VIEWER_TAG = "photoViewer"

/** The brand-colour header above the card sheet. */
private val HEADER_HEIGHT = 200.dp

/** A 1D barcode's height in scan mode, when it runs across the screen (landscape). */
private val SCAN_MODE_LINEAR_HEIGHT = 200.dp

/** The barcode's white surface on the card sheet. */
const val BARCODE_SURFACE_TAG = "barcodeSurface"


@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.DetailScreen(
    cardId: String,
    color: String,
    added: Boolean = false,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
    directions: DetailDirections,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val card by viewModel.card.collectAsState()
    val deletion by viewModel.deletion.collectAsState()
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(deletion) {
        if (deletion == Deletion.DELETED) directions.toBack()
    }
    // At the till the barcode must be readable, whatever the user's brightness setting.
    FullBrightness()
    val brandColor = color.toBrandColorOrNull() ?: MaterialTheme.colorScheme.surfaceVariant
    val barcode = remember(card.barcodeContent, card.barcodeFormat) {
        BarcodeEncoder.encode(card.barcodeContent, card.barcodeFormat)
    }
    var scanMode by rememberSaveable { mutableStateOf(false) }
    /** The side whose photo is shown full screen (#10), kept through a rotation. */
    var openPhoto by rememberSaveable { mutableStateOf<CardPhotoSide?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    // A card just added offers another once (#6). Kept until the snackbar ends, so turning the phone shows it again,
    // and gone after that or after Edit, so it isn't made again on return from the picker or the form. Scan mode
    // would hide it while it times out, so it waits until scan mode closes.
    var offeringAnother by rememberSaveable { mutableStateOf(added) }
    val cardAdded = stringResource(R.string.card_added)
    val addAnother = stringResource(R.string.add_another_card)
    LaunchedEffect(offeringAnother, scanMode) {
        if (!offeringAnother || scanMode) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(cardAdded, actionLabel = addAnother, duration = SnackbarDuration.Long)
        offeringAnother = false
        if (result == SnackbarResult.ActionPerformed) directions.toAddCard()
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = card.store.name) },
                    navigationIcon = {
                        IconButton(onClick = { directions.toBack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                offeringAnother = false
                                directions.toEdit(card.id, card.store.id)
                            },
                            enabled = card.id.isNotEmpty(),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                        }
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = brandColor
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = Modifier.sharedElement(
                // From the route, like the colour: the loaded card's id is empty until Room emits (#16).
                sharedContentState = rememberSharedContentState(key = cardTransitionKey(cardId)),
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
                        .height(HEADER_HEIGHT)
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
                                .padding(Spacing.l)
                        )
                    } else {
                        Text(
                            text = card.store.name,
                            style = MaterialTheme.typography.headlineLarge,
                            color = brandColor.contrastingTextColor(),
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
                            .padding(Spacing.l),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        card.label?.let { Text(text = it, style = MaterialTheme.typography.titleMedium) }
                        Text(text = card.store.name)
                        if (barcode != null) {
                            val showFullScreen = stringResource(R.string.show_full_screen)
                            Box(
                                modifier = Modifier
                                    .padding(top = Spacing.xl)
                                    .fillMaxWidth()
                                    // White whatever the theme, with a margin for the quiet zone: scanners need it.
                                    .background(Color.White, MaterialTheme.shapes.medium)
                                    .testTag(BARCODE_SURFACE_TAG)
                                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scanMode = true }) }
                                    .semantics { onClick(label = showFullScreen) { scanMode = true; true } }
                                    .padding(Spacing.l),
                                contentAlignment = Alignment.Center,
                            ) {
                                Barcode(barcode, Modifier.barcodeSize(barcode))
                            }
                            // The card's own format couldn't be drawn (no writer, or content it can't hold).
                            if (barcode.format != card.barcodeFormat) {
                                Text(
                                    text = stringResource(R.string.barcode_shown_as, barcodeFormatName(barcode.format)),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = Spacing.xs),
                                )
                            }
                        }
                        CopyableNumber(
                            number = card.displayNumber,
                            // With the barcode: the till reads one or the other.
                            modifier = Modifier.padding(top = Spacing.m),
                        )
                        // Under the number, so the barcode keeps its place at the top (#9).
                        card.note?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .padding(top = Spacing.xl)
                                    .fillMaxWidth()
                                    .testTag(CARD_NOTE_TAG),
                            )
                        }
                        // Last: the photos are for the user, the barcode and number are for the till (#10).
                        val photos = CardPhotoSide.entries.mapNotNull { side -> card.photo(side)?.let { side to it } }
                        if (photos.isNotEmpty()) {
                            Row(
                                // With the note when there is one, a group of their own after the number.
                                modifier = Modifier.padding(top = if (card.note != null) Spacing.m else Spacing.xl).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                            ) {
                                photos.forEach { (side, name) ->
                                    AsyncImage(
                                        model = CardPhoto(name),
                                        contentDescription = side.photoDescription,
                                        imageLoader = hiltImageLoader(),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(CARD_ASPECT_RATIO)
                                            .clip(CardShape)
                                            .clickable(onClickLabel = stringResource(R.string.show_full_screen)) { openPhoto = side },
                                    )
                                }
                                // A single photo keeps the size it has next to the other.
                                if (photos.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        if (scanMode && barcode != null) {
            ScanMode(barcode, card.displayNumber, onExit = { scanMode = false })
        }
        openPhoto?.let { side ->
            card.photo(side)?.let { PhotoViewer(it, side.photoDescription, onExit = { openPhoto = null }) }
        }
        if (confirmingDelete && deletion != Deletion.DELETED) {
            ConfirmDelete(
                failed = deletion == Deletion.FAILED,
                deleting = deletion == Deletion.DELETING,
                onConfirm = viewModel::delete,
                onDismiss = {
                    confirmingDelete = false
                    viewModel.dismissDeletion()
                },
            )
        }
    }
}

/** Asks before deleting the card. A failed delete is said here, and "Delete" tries again. */
@Composable
private fun ConfirmDelete(failed: Boolean, deleting: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // The list's dialog for one card: the same words.
        title = { Text(pluralStringResource(R.plurals.delete_cards_question, 1)) },
        text = {
            Text(
                pluralStringResource(if (failed) R.plurals.delete_cards_failed else R.plurals.delete_cards_explanation, 1),
                color = if (failed) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !deleting) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * The barcode alone, as large as the screen allows, black on white, with the screen kept on. In portrait a 1D barcode
 * takes all the height the number leaves, and [Barcode] runs it down the long side (#34). A tap or back closes it.
 */
@Composable
private fun ScanMode(barcode: EncodedBarcode, number: String, onExit: () -> Unit) {
    KeepScreenOn()
    BackHandler(onBack = onExit)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(onClickLabel = stringResource(R.string.close_full_screen), onClick = onExit)
            .testTag("scanMode")
            // Inside the system bars: the app draws edge to edge (#108, the number sat under the navigation bar).
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(Spacing.xl),
    ) {
        val alongTheHeight = barcode.isLinear && maxHeight > maxWidth
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Weighted, so it is measured after the number and gets only the height left (landscape).
            Barcode(
                barcode,
                if (alongTheHeight) Modifier.weight(1f).fillMaxWidth()
                else Modifier.weight(1f, fill = false).barcodeSize(barcode, linearHeight = SCAN_MODE_LINEAR_HEIGHT),
            )
            Text(
                text = number,
                color = Color.Black,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = Spacing.xl),
            )
        }
    }
}

private fun LoyaltyCard.photo(side: CardPhotoSide): String? =
    if (side == CardPhotoSide.FRONT) frontPhotoPath else backPhotoPath

/** One of the card's photos (#10), whole, on black over the whole screen. A tap or back closes it. */
@Composable
private fun PhotoViewer(photo: String, description: String, onExit: () -> Unit) {
    BackHandler(onBack = onExit)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClickLabel = stringResource(R.string.close_full_screen), onClick = onExit)
            .testTag(PHOTO_VIEWER_TAG)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(Spacing.l),
    ) {
        AsyncImage(
            model = CardPhoto(photo),
            contentDescription = description,
            imageLoader = hiltImageLoader(),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The number in large type; a tap copies it, exactly as shown. */
@Composable
private fun CopyableNumber(number: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.card_number_clip_label)
    val copied = stringResource(R.string.number_copied)
    Text(
        text = number,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier.clickable(onClickLabel = stringResource(R.string.copy_number)) {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(clipLabel, number))) }
            // From Android 13 the system confirms a copy itself.
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            }
        },
    )
}
