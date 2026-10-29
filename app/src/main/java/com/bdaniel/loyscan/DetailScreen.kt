package com.bdaniel.loyscan

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.bdaniel.loyscan.ui.FullBrightness
import com.bdaniel.loyscan.ui.KeepScreenOn
import com.bdaniel.loyscan.ui.barcode.Barcode
import com.bdaniel.loyscan.ui.barcode.BarcodeEncoder
import com.bdaniel.loyscan.ui.barcode.EncodedBarcode
import com.bdaniel.loyscan.ui.barcode.barcodeSize
import com.bdaniel.loyscan.ui.hiltImageLoader
import com.bdaniel.loyscan.ui.contrastingTextColor
import com.bdaniel.loyscan.ui.toBrandColorOrNull
import com.bdaniel.loyscan.ui.theme.icons.Icons
import kotlinx.coroutines.launch


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
    val snackbarHostState = remember { SnackbarHostState() }
    // A card just added offers another once (#6). Kept until the snackbar ends, so turning the phone shows it again,
    // and gone after that or after Edit, so it isn't made again on return from the picker or the form. Scan mode
    // would hide it while it times out, so it waits until scan mode closes.
    var offeringAnother by rememberSaveable { mutableStateOf(added) }
    LaunchedEffect(offeringAnother, scanMode) {
        if (!offeringAnother || scanMode) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar("Card added", actionLabel = "Add another", duration = SnackbarDuration.Long)
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
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
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
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        card.label?.let { Text(text = it, style = MaterialTheme.typography.titleMedium) }
                        Text(text = card.store.name)
                        if (barcode != null) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 16.dp)
                                    .fillMaxWidth()
                                    // White whatever the theme, with a margin for the quiet zone: scanners need it.
                                    .background(Color.White, RoundedCornerShape(8.dp))
                                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scanMode = true }) }
                                    .semantics { onClick(label = "Show full screen") { scanMode = true; true } }
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Barcode(barcode, Modifier.barcodeSize(barcode))
                            }
                            // The card's own format couldn't be drawn (no writer, or content it can't hold).
                            if (barcode.format != card.barcodeFormat) {
                                Text(
                                    text = "Shown as ${barcode.format.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
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
        if (scanMode && barcode != null) {
            ScanMode(barcode, card.displayNumber, onExit = { scanMode = false })
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
        title = { Text("Delete this card?") },
        text = {
            Text(
                if (failed) "The card couldn't be deleted. Try again." else "It is removed from this phone for good.",
                color = if (failed) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !deleting) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The barcode alone, as large as the screen allows, black on white, with the screen kept on. A tap or back closes
 * it.
 */
@Composable
private fun ScanMode(barcode: EncodedBarcode, number: String, onExit: () -> Unit) {
    KeepScreenOn()
    BackHandler(onBack = onExit)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(onClickLabel = "Close full screen", onClick = onExit)
            .testTag("scanMode")
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Weighted, so it is measured after the number and gets only the height left (landscape).
        Barcode(barcode, Modifier.weight(1f, fill = false).barcodeSize(barcode, linearHeight = 200.dp))
        Text(
            text = number,
            color = Color.Black,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = 24.dp),
        )
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
