package com.bdaniel.loyscan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bdaniel.loyscan.ui.LogoPreview
import com.bdaniel.loyscan.ui.theme.Spacing
import com.bdaniel.loyscan.ui.barcodeFormatName
import com.bdaniel.loyscan.ui.theme.icons.Icons
import com.bdaniel.loyscan.ui.toBrandColorOrNull

const val CUSTOM_STORE_NAME_TAG = "customStoreName"

/**
 * "Other card": a store that isn't listed, with a name, a colour and the barcode format for its typed numbers.
 * Creating it opens the scanner for it. Editing a custom store (#53) saves it and goes back; it can also be deleted,
 * after a confirmation that says how many cards go with it, and its logo is shown with a way to its logo screen (#151).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomStoreScreen(
    directions: CustomStoreDirections,
    viewModel: CustomStoreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.createdStoreId) {
        state.createdStoreId?.let(directions.onCreated)
    }
    LaunchedEffect(state.saved, state.deletion) {
        if (state.saved || state.deletion == Deletion.DELETED) directions.toBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.editing) R.string.edit_store else R.string.new_store)) },
                navigationIcon = {
                    IconButton(onClick = directions.toBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (state.editing && state.ready) {
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                },
            )
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        if (state.storeGone) {
            Text(stringResource(R.string.store_gone), modifier = Modifier.padding(innerPadding).padding(Spacing.l))
        }
        if (confirmingDelete && state.ready && state.deletion != Deletion.DELETED) {
            ConfirmDelete(
                storeName = state.storedName.orEmpty(),
                cardCount = state.cardCount,
                failed = state.deletion == Deletion.FAILED,
                deleting = state.deletion == Deletion.DELETING,
                onConfirm = viewModel::delete,
                onDismiss = {
                    confirmingDelete = false
                    viewModel.dismissDeletion()
                },
            )
        }
        if (!state.ready) return@Scaffold
        Column(
            Modifier.padding(innerPadding).verticalScroll(rememberScrollState()).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth().testTag(CUSTOM_STORE_NAME_TAG),
                label = { Text(stringResource(R.string.store_name)) },
                singleLine = true,
            )
            Text(stringResource(R.string.store_colour), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                CustomStoreViewModel.COLORS.forEach { swatch ->
                    val selected = swatch.hex == state.color
                    val swatchName = stringResource(swatch.name)
                    Box(
                        Modifier
                            .size(SWATCH_SIZE)
                            .clip(CircleShape)
                            .background(swatch.hex.toBrandColorOrNull() ?: MaterialTheme.colorScheme.surfaceVariant)
                            .then(
                                if (selected) Modifier.border(SWATCH_SELECTED_BORDER, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .selectable(selected = selected, role = Role.RadioButton) {
                                viewModel.onColorChange(swatch.hex)
                            }
                            .semantics { contentDescription = swatchName }
                    )
                }
            }
            Text(stringResource(R.string.barcode_for_typed_numbers), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                CustomStoreViewModel.FORMATS.forEach { format ->
                    FilterChip(
                        selected = format == state.format,
                        onClick = { viewModel.onFormatChange(format) },
                        label = { Text(barcodeFormatName(format)) },
                    )
                }
            }
            Text(
                stringResource(R.string.scanned_card_keeps_its_barcode),
                style = MaterialTheme.typography.bodyMedium,
            )
            val storeId = state.storeId
            if (storeId != null) { // the edit form only: a new store has no id before Create (#151)
                Text(stringResource(R.string.logo), style = MaterialTheme.typography.titleSmall)
                LogoPreview(name = state.name, color = state.color, logo = state.logo, logoTreatment = state.logoTreatment)
                OutlinedButton(onClick = { directions.toStoreLogo(storeId) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.change_logo), textAlign = TextAlign.Center)
                }
            }
            if (state.saveFailed) {
                Text(
                    stringResource(R.string.store_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(onClick = viewModel::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.editing) R.string.save else R.string.create))
            }
        }
    }
}

/** Asks before deleting the store, saying how many cards go with it. A failed delete is said here, and "Delete" tries again. */
@Composable
private fun ConfirmDelete(
    storeName: String,
    cardCount: Int,
    failed: Boolean,
    deleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_store_question, storeName)) },
        text = {
            Text(
                when {
                    failed -> stringResource(R.string.delete_store_failed)
                    // Not a plural's "zero": English and Hungarian use "other" for 0.
                    cardCount == 0 -> stringResource(R.string.store_has_no_cards)
                    else -> pluralStringResource(R.plurals.store_cards_deleted_with_it, cardCount, cardCount)
                },
                color = if (failed) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !deleting) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A colour swatch, large enough to tap. */
private val SWATCH_SIZE = 40.dp

/** The ring around the chosen swatch. */
private val SWATCH_SELECTED_BORDER = 3.dp
