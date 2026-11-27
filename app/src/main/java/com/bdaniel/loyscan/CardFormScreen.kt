package com.bdaniel.loyscan

import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.bdaniel.loyscan.ui.theme.CardShape
import com.bdaniel.loyscan.ui.theme.Spacing
import com.bdaniel.loyscan.CardFormState.NumberCheck
import com.bdaniel.loyscan.domain.cardnumber.Validation
import com.bdaniel.loyscan.ui.CARD_ASPECT_RATIO
import com.bdaniel.loyscan.ui.CardPhoto
import com.bdaniel.loyscan.ui.PhotoCamera
import com.bdaniel.loyscan.ui.LeaveFormDialog
import com.bdaniel.loyscan.ui.PhotoCropScreen
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.ui.rememberPhotoCamera
import com.bdaniel.loyscan.ui.hiltImageLoader
import com.bdaniel.loyscan.ui.barcodeFormatName
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
    // Back with changes asks first (#209). The crop screen's own Back still cancels the crop: it registers its callback
    // after this one, whether it opens later or is composed with the form on a restore, and the last one wins.
    var leaving by rememberSaveable { mutableStateOf(false) }
    val onBack = { if (state.hasChanges) leaving = true else directions.toBack() }
    BackHandler(enabled = state.hasChanges) { leaving = true }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.editing) R.string.edit_card else R.string.add_card)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        if (store == null) {
            if (state.storeGone) {
                Text(stringResource(R.string.store_gone), modifier = Modifier.padding(innerPadding).padding(Spacing.l))
            }
            return@Scaffold
        }
        val problem = problemText(state.check, store.name)
        Column(
            Modifier.padding(innerPadding).verticalScroll(rememberScrollState()).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                StoreDot(store, STORE_DOT_LARGE)
                Text(store.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (state.otherStores.isNotEmpty()) {
                    TextButton(onClick = { choosingStore = true }) { Text(stringResource(R.string.change_store)) }
                }
            }
            val shownAs = state.shownAs?.let { stringResource(R.string.barcode_shown_as, it) }
            OutlinedTextField(
                value = state.number,
                onValueChange = viewModel::onNumberChange,
                modifier = Modifier.fillMaxWidth().testTag(CARD_NUMBER_TAG),
                label = { Text(stringResource(R.string.card_number)) },
                readOnly = !state.numberEditable,
                isError = problem != null,
                supportingText = (problem ?: shownAs)?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (state.numberDigitsOnly) {
                        KeyboardType.Number
                    } else {
                        KeyboardType.Text
                    }
                ),
            )
            if (!state.numberEditable) {
                TextButton(onClick = viewModel::typeNumberInstead) { Text(stringResource(R.string.type_number_instead)) }
            }
            if (state.suggestedStores.isNotEmpty()) {
                Column {
                    Text(stringResource(R.string.looks_like_card_from), style = MaterialTheme.typography.bodyMedium)
                    state.suggestedStores.forEach { suggested ->
                        TextButton(onClick = { viewModel.switchStore(suggested.id) }) { Text(suggested.name) }
                    }
                }
            }
            state.barcodeFormat?.let { format ->
                if (state.barcodeFormatChoices.isEmpty()) { // an untouched scan is drawn as scanned (#187)
                    Text(
                        stringResource(
                            if (state.scannedFormat != null) R.string.scanned_format else R.string.typed_number_barcode,
                            barcodeFormatName(format),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    BarcodeTypeSelector(format, state.barcodeFormatChoices, viewModel::onBarcodeFormatChange)
                }
            }
            OutlinedTextField(
                value = state.label,
                onValueChange = viewModel::onLabelChange,
                modifier = Modifier.fillMaxWidth().testTag(CARD_LABEL_TAG),
                label = { Text(stringResource(R.string.label_optional)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                modifier = Modifier.fillMaxWidth().testTag(CARD_NOTE_TAG),
                label = { Text(stringResource(R.string.note_optional)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
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
            if (state.frontPhoto != null) {
                val canToggle = !state.saving && state.savedCardId == null
                PhotoSwitch(
                    text = stringResource(R.string.show_photo_on_list),
                    checked = state.showPhotoOnList,
                    enabled = canToggle,
                    onCheckedChange = viewModel::onShowPhotoOnListChange,
                )
                PhotoSwitch(
                    text = stringResource(R.string.show_photo_on_widget),
                    checked = state.showPhotoOnWidget,
                    enabled = canToggle,
                    onCheckedChange = viewModel::onShowPhotoOnWidgetChange,
                )
            }
            if (state.photoFailed) {
                Text(
                    stringResource(R.string.photo_add_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            when (cameraProblem) {
                PhotoCamera.Result.DENIED -> Text(
                    stringResource(R.string.camera_needed_for_photo),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                PhotoCamera.Result.DENIED_PERMANENTLY -> Column {
                    Text(
                        stringResource(R.string.camera_turned_off),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = camera::openSettings) { Text(stringResource(R.string.open_settings)) }
                }
                else -> Unit
            }
            if (state.saveFailed) {
                Text(
                    stringResource(R.string.card_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(onClick = viewModel::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (problem != null) R.string.save_anyway else R.string.save))
            }
        }
        if (leaving && state.hasChanges) {
            LeaveFormDialog(
                title = stringResource(if (state.editing) R.string.save_changes_question else R.string.discard_card_question),
                // The form's own Save: "Save anyway" for a number that fails its check, as the button below says.
                saveText = if (state.canSave) stringResource(if (problem != null) R.string.save_anyway else R.string.save) else null,
                onSave = {
                    leaving = false
                    viewModel.save()
                },
                onDiscard = {
                    leaving = false
                    viewModel.discard()
                    directions.toBack()
                },
                onKeepEditing = { leaving = false },
            )
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
        state.cropping?.let { cropping ->
            PhotoCropScreen(
                photo = cropping.photo,
                description = cropping.side.photoDescription,
                onConfirm = viewModel::confirmCrop,
                onCancel = viewModel::cancelCrop,
            )
        }
    }
}

/** A photo's content description: "Front photo", "Back photo". */
internal val CardPhotoSide.photoDescription: String
    @Composable get() = stringResource(if (this == CardPhotoSide.FRONT) R.string.front_photo else R.string.back_photo)

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
    val front = side == CardPhotoSide.FRONT
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
                        .clip(CardShape),
                )
                Row {
                    IconButton(onClick = onPick, enabled = enabled) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(
                                if (front) R.string.replace_front_photo else R.string.replace_back_photo
                            ),
                        )
                    }
                    IconButton(onClick = onRemove, enabled = enabled) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(
                                if (front) R.string.remove_front_photo else R.string.remove_back_photo
                            ),
                        )
                    }
                }
            }
            else -> OutlinedButton(onClick = onPick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(if (front) R.string.add_front_photo else R.string.add_back_photo),
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (onTake != null) {
            Box {
                DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.take_photo)) }, onClick = {
                        choosing = false
                        onTake()
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.choose_from_gallery)) }, onClick = {
                        choosing = false
                        onChoose()
                    })
                }
            }
        }
    }
}

/** Why the number may be wrong, in the phone's language (#72), or `null` when there is nothing to say. */
@Composable
private fun problemText(check: NumberCheck, storeName: String): String? = when (check) {
    NumberCheck.Empty, NumberCheck.Valid -> null
    is NumberCheck.DoesNotFitFormat -> stringResource(R.string.number_does_not_fit_format, barcodeFormatName(check.format))
    NumberCheck.ScanDoesNotMatchStore -> stringResource(R.string.scan_does_not_match_store, storeName)
    // `map` is inline, so it can read resources; `joinToString`'s transform isn't composable.
    is NumberCheck.Invalid -> check.errors.sorted().map { error ->
        when (error) {
            Validation.Error.EMPTY -> ""
            Validation.Error.INVALID_CHARACTERS -> stringResource(R.string.number_has_invalid_characters, storeName)
            Validation.Error.TOO_SHORT -> stringResource(R.string.number_too_short, storeName)
            Validation.Error.TOO_LONG -> stringResource(R.string.number_too_long, storeName)
            Validation.Error.WRONG_START -> stringResource(R.string.number_starts_wrong, storeName)
            Validation.Error.BAD_CHECK_DIGIT -> stringResource(R.string.number_bad_check_digit)
        }
    }.joinToString(" ")
}

/**
 * The card's type (#187), collapsed to one line for the many who never change it; tapping it shows [choices] as chips,
 * and picking one collapses it again.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BarcodeTypeSelector(format: BarcodeFormat, choices: List<BarcodeFormat>, onPick: (BarcodeFormat) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val changeLabel = stringResource(R.string.change_barcode_type)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LocalMinimumInteractiveComponentSize.current) // the row is the touch target
                .clickable(onClickLabel = changeLabel, role = Role.Button) { expanded = !expanded },
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.typed_number_barcode, barcodeFormatName(format)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Default.Edit, contentDescription = null)
        }
        if (expanded) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                choices.forEach { choice ->
                    FilterChip(
                        selected = choice == format,
                        onClick = {
                            onPick(choice)
                            expanded = false
                        },
                        label = { Text(barcodeFormatName(choice)) },
                    )
                }
            }
        }
    }
}

/**
 * A switch for where the card's front photo is shown: My cards (#184) or the widget (#185). The whole row toggles, so
 * TalkBack reads one switch named by its [text].
 */
@Composable
private fun PhotoSwitch(text: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The Switch has no handler of its own, so it adds no touch-target padding: the row is the target, at
            // least Material's 48dp tall (review of #184).
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
