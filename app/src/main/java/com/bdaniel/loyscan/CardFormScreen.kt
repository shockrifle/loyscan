package com.bdaniel.loyscan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bdaniel.loyscan.CardFormState.NumberCheck
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import com.bdaniel.loyscan.domain.cardnumber.Validation
import com.bdaniel.loyscan.ui.theme.icons.Icons
import com.bdaniel.loyscan.ui.toBrandColorOrNull

const val CARD_NUMBER_TAG = "cardNumber"
const val CARD_LABEL_TAG = "cardLabel"

/**
 * Last step of adding a card: the number (scanned or typed), checked live against the store's rule, and an optional
 * label. A number that fails the check can still be saved ("Save anyway"); a scan another store's rule reads offers
 * that store. Saving opens the new card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardFormScreen(
    directions: CardFormDirections,
    viewModel: CardFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val store = state.store

    LaunchedEffect(state.savedCardId) {
        val id = state.savedCardId
        if (id != null && store != null) directions.toCard(id, store.color)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add card") },
                navigationIcon = {
                    IconButton(onClick = directions.toBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        if (store == null) return@Scaffold
        Column(
            Modifier.padding(innerPadding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(store.color.toBrandColorOrNull() ?: MaterialTheme.colorScheme.surfaceVariant)
                )
                Text(store.name, style = MaterialTheme.typography.titleLarge)
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
    }
}

/** Why the number may be wrong, or `null` when there is nothing to say. */
private fun problemText(check: NumberCheck, storeName: String): String? = when (check) {
    NumberCheck.Empty, NumberCheck.Valid -> null
    is NumberCheck.DoesNotFitFormat -> "This number can't be drawn as ${check.format.displayName}."
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
