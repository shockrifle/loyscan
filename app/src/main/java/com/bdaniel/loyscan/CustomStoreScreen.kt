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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bdaniel.loyscan.ui.theme.icons.Icons
import com.bdaniel.loyscan.ui.toBrandColorOrNull

const val CUSTOM_STORE_NAME_TAG = "customStoreName"

/**
 * "Other card": a store that isn't listed, with a name, a colour and the barcode format for its typed numbers.
 * Creating it opens the scanner for it. Editing a custom store (#53) saves it and goes back.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomStoreScreen(
    directions: CustomStoreDirections,
    viewModel: CustomStoreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.createdStoreId) {
        state.createdStoreId?.let(directions.toScanner)
    }
    LaunchedEffect(state.saved) {
        if (state.saved) directions.toBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.editing) "Edit store" else "New store") },
                navigationIcon = {
                    IconButton(onClick = directions.toBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        if (state.storeGone) {
            Text("This store is no longer available.", modifier = Modifier.padding(innerPadding).padding(16.dp))
        }
        if (!state.ready) return@Scaffold
        Column(
            Modifier.padding(innerPadding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth().testTag(CUSTOM_STORE_NAME_TAG),
                label = { Text("Store name") },
                singleLine = true,
            )
            Text("Colour", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CustomStoreViewModel.COLORS.forEach { swatch ->
                    val selected = swatch.hex == state.color
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(swatch.hex.toBrandColorOrNull() ?: MaterialTheme.colorScheme.surfaceVariant)
                            .then(
                                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .selectable(selected = selected, role = Role.RadioButton) {
                                viewModel.onColorChange(swatch.hex)
                            }
                            .semantics { contentDescription = swatch.name }
                    )
                }
            }
            Text("Barcode for typed numbers", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomStoreViewModel.FORMATS.forEach { format ->
                    FilterChip(
                        selected = format == state.format,
                        onClick = { viewModel.onFormatChange(format) },
                        label = { Text(format.displayName) },
                    )
                }
            }
            Text(
                "A scanned card keeps the barcode it was scanned in.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.saveFailed) {
                Text(
                    "The store couldn't be saved. Try again.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(onClick = viewModel::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.editing) "Save" else "Create")
            }
        }
    }
}
