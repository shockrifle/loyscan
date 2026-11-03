package com.bdaniel.loyscan

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.bdaniel.loyscan.CardFormState.NumberCheck
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import com.bdaniel.loyscan.domain.cardnumber.Validation
import com.bdaniel.loyscan.ui.CARD_ASPECT_RATIO
import com.bdaniel.loyscan.ui.CardPhoto
import com.bdaniel.loyscan.ui.PhotoCamera
import com.bdaniel.loyscan.ui.rememberPhotoCamera
import com.bdaniel.loyscan.ui.hiltImageLoader
import com.bdaniel.loyscan.ui.scanner.PicturePicker
import com.bdaniel.loyscan.ui.scanner.rememberPicturePicker
import com.bdaniel.loyscan.ui.theme.icons.Icons

const val CARD_NUMBER_TAG = "cardNumber"
const val CARD_LABEL_TAG = "cardLabel"
const val CARD_NOTE_TAG = "cardNote"

/**
 * Last step of adding a card, or editing one (#8): the number (scanned or typed), checked live against the store's rule,
 * and an optional label and note (#9). A number that fails the check can still be saved ("Save anyway"); a scan another store's rule
 * reads offers that store, and an edited card can be moved to any other store. Saving opens the card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardFormScreen(
    directions: CardFormDirections,
    viewModel: CardFormViewModel = hiltViewModel(),
    /** Opens the system photo picker for a card photo (#10); a fake in tests. */
    photoPicker: @Composable (onPicked: (Uri?) -> Unit) -> PicturePicker = { rememberPicturePicker(it) },
    /** Takes a card photo with the camera app (#10); a fake in tests. */
    photoCamera: @Composable (onResult: (PhotoCamera.Result) -> Unit) -> PhotoCamera = { rememberPhotoCamera(it) },
) {
    val state by viewModel.state.collectAsState()
    val store = state.store
    var choosingStore by rememberSaveable { mutableStateOf(false) }
    // Why the camera couldn't be opened for a photo, until the next photo is taken or picked.
    var cameraProblem by rememberSaveable { mutableStateOf<PhotoCamera.Result?>(null) }
    val camera = photoCamera { result ->
        viewModel.onPhotoTaken(taken = result == PhotoCamera.Result.TAKEN)
        if (result == PhotoCamera.Result.DENIED || result == PhotoCamera.Result.DENIED_PERMANENTLY) cameraProblem = result
    }

    LaunchedEffect(state.savedCardId) {
        val id = state.savedCardId
        if (id != null && store != null) directions.toCard(id, store.color)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.editing) "Edit card" else "Add card") },
                navigationIcon = {
                    IconButton(onClick = directions.toBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        if (store == null) {
            if (state.storeGone) {
                Text("This store is no longer available.", modifier = Modifier.padding(innerPadding).padding(16.dp))
            }
            return@Scaffold
        }
        Column(
            Modifier.padding(innerPadding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StoreDot(store, 32.dp)
                Text(store.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (state.otherStores.isNotEmpty()) {
                    TextButton(onClick = { choosingStore = true }) { Text("Change store") }
                }
            }
            val problem = problemText(state.check, store.name)
            OutlinedTextField(
                value = state.number,
                onValueChange = viewModel::onNumberChange,
                modifier = Modifier.fillMaxWidth().testTag(CARD_NUMBER_TAG),
                label = { Text("Card number") },
                readOnly = !state.numberEditable,
                isError = problem != null,
                supportingText = (problem ?: state.shownAs?.let { "Shown as $it" })?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (store.cardNumberRule?.charset == CardNumberRule.Charset.DIGITS) {
                        KeyboardType.Number
                    } else {
                        KeyboardType.Text
                    }
                ),
            )
            if (!state.numberEditable) {
                TextButton(onClick = viewModel::typeNumberInstead) { Text("Type the number instead") }
            }
            if (state.suggestedStores.isNotEmpty()) {
                Column {
                    Text("It looks like a card from:", style = MaterialTheme.typography.bodyMedium)
                    state.suggestedStores.forEach { suggested ->
                        TextButton(onClick = { viewModel.switchStore(suggested.id) }) { Text(suggested.name) }
                    }
                }
            }
            state.barcodeFormat?.let { format ->
                Text(
                    if (state.scannedFormat != null) "Scanned · ${format.displayName}" else "Barcode: ${format.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedTextField(
                value = state.label,
                onValueChange = viewModel::onLabelChange,
                modifier = Modifier.fillMaxWidth().testTag(CARD_LABEL_TAG),
                label = { Text("Label (optional)") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                modifier = Modifier.fillMaxWidth().testTag(CARD_NOTE_TAG),
                label = { Text("Note (optional)") },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CardPhotoSide.entries.forEach { side ->
                    val picker = photoPicker { viewModel.onPhotoPicked(side, it) }
                    PhotoSlot(
                        side = side,
                        photo = if (side == CardPhotoSide.FRONT) state.frontPhoto else state.backPhoto,
                        importing = state.importingPhoto == side,
                        // One picture at a time, and none once saving has started.
                        enabled = state.importingPhoto == null && !state.saving && state.savedCardId == null,
                        onChoose = {
                            cameraProblem = null
                            picker.pick()
                        },
                        onTake = if (camera.available) {
                            {
                                cameraProblem = null
                                viewModel.startCapture(side)?.let(camera::take)
                            }
                        } else {
                            null
                        },
                        onRemove = { viewModel.removePhoto(side) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (state.photoFailed) {
                Text(
                    "The photo couldn't be added. Try again.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            when (cameraProblem) {
                PhotoCamera.Result.DENIED -> Text(
                    "LoyScan needs the camera to take a photo of your card.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                PhotoCamera.Result.DENIED_PERMANENTLY -> Column {
                    Text(
                        "Camera access is turned off for LoyScan. You can allow it in the app's settings.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = camera::openSettings) { Text("Open settings") }
                }
                else -> Unit
            }
            if (state.saveFailed) {
                Text(
                    "The card couldn't be saved. Try again.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(onClick = viewModel::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                Text(if (problem != null) "Save anyway" else "Save")
            }
        }
        if (choosingStore) {
            StoreChooser(
                stores = state.otherStores,
                onChoose = { chosen ->
                    viewModel.switchStore(chosen.id)
                    choosingStore = false
                },
                onDismiss = { choosingStore = false },
            )
        }
    }
}

/** "front" or "back", for a photo's texts (#10). */
internal val CardPhotoSide.sideName: String get() = if (this == CardPhotoSide.FRONT) "front" else "back"

/** A photo's content description: "Front photo", "Back photo". */
internal val CardPhotoSide.photoDescription: String get() = "${sideName.replaceFirstChar { it.uppercase() }} photo"

/**
 * One side's photo (#10): the photo in the card's proportions with Replace and Remove, a progress indicator while a
 * picture is copied in, or a button to add one. Add and Replace offer [onTake] and [onChoose] in a menu; without a
 * camera app ([onTake] `null`) they choose from the gallery at once.
 */
@Composable
private fun PhotoSlot(
    side: CardPhotoSide,
    photo: String?,
    importing: Boolean,
    enabled: Boolean,
    onChoose: () -> Unit,
    onTake: (() -> Unit)?,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sideName = side.sideName
    var choosing by remember { mutableStateOf(false) }
    val onPick = { if (onTake == null) onChoose() else choosing = true }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            importing -> Box(Modifier.fillMaxWidth().aspectRatio(CARD_ASPECT_RATIO), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            photo != null -> {
                AsyncImage(
                    model = CardPhoto(photo),
                    contentDescription = side.photoDescription,
                    imageLoader = hiltImageLoader(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(CARD_ASPECT_RATIO)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Row {
                    IconButton(onClick = onPick, enabled = enabled) {
                        Icon(Icons.Default.Edit, contentDescription = "Replace $sideName photo")
                    }
                    IconButton(onClick = onRemove, enabled = enabled) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove $sideName photo")
                    }
                }
            }
            else -> OutlinedButton(onClick = onPick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text("Add $sideName photo", textAlign = TextAlign.Center)
            }
        }
        if (onTake != null) {
            Box {
                DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                    DropdownMenuItem(text = { Text("Take photo") }, onClick = {
                        choosing = false
                        onTake()
                    })
                    DropdownMenuItem(text = { Text("Choose from gallery") }, onClick = {
                        choosing = false
                        onChoose()
                    })
                }
            }
        }
    }
}

/** Why the number may be wrong, or `null` when there is nothing to say. */
private fun problemText(check: NumberCheck, storeName: String): String? = when (check) {
    NumberCheck.Empty, NumberCheck.Valid -> null
    is NumberCheck.DoesNotFitFormat -> "This isn't a valid ${check.format.displayName} number."
    NumberCheck.ScanDoesNotMatchStore -> "This barcode doesn't look like a $storeName card."
    is NumberCheck.Invalid -> check.errors.sorted().joinToString(" ") { error ->
        when (error) {
            Validation.Error.EMPTY -> ""
            Validation.Error.INVALID_CHARACTERS -> "It has characters $storeName card numbers don't use."
            Validation.Error.TOO_SHORT -> "Too short for a $storeName card number."
            Validation.Error.TOO_LONG -> "Too long for a $storeName card number."
            Validation.Error.WRONG_START -> "$storeName card numbers don't start like this."
            Validation.Error.BAD_CHECK_DIGIT -> "The check digit doesn't match. Check the number for a typo."
        }
    }
}
