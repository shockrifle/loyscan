package com.bdaniel.loyscan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bdaniel.loyscan.domain.Store
import com.bdaniel.loyscan.ui.theme.Spacing
import com.bdaniel.loyscan.ui.theme.icons.Icons

// Not store ids: catalogue ids are slugs and custom ones UUIDs.
private const val OTHER_CARD_KEY = "other card"
private const val SUGGESTED_KEY = "suggested"
private const val FREQUENTLY_ADDED_KEY = "frequently added"
private const val ALL_STORES_KEY = "all stores"

/**
 * First step of adding a card: pick the store, searching by name or alias, or "Other card" for one that isn't listed.
 * While nothing is searched, the frequently added stores of the user's country come first (#6), after the stores
 * suggested for a shared picture's barcode (#77). A custom store has an Edit button (#53), a catalogue store a Logo
 * button (#151).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorePickerScreen(
    directions: StorePickerDirections,
    viewModel: StorePickerViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val stores by viewModel.stores.collectAsState()
    val suggested by viewModel.suggested.collectAsState()
    val frequentlyAdded by viewModel.frequentlyAdded.collectAsState()
    // A custom store is edited in its form (#53); a catalogue store's logo has its own screen (#151). In every section.
    val trailing: (Store) -> (@Composable () -> Unit)? = { store ->
        if (store.isCustom) {
            {
                IconButton(onClick = { directions.toEditStore(store.id) }) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_store_named, store.name))
                }
            }
        } else {
            {
                IconButton(onClick = { directions.toStoreLogo(store.id) }) {
                    Icon(Icons.Default.Image, contentDescription = stringResource(R.string.logo_of_store, store.name))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.choose_store)) },
                navigationIcon = {
                    IconButton(onClick = directions.toBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        StoreSearchList(
            query = query,
            onQueryChange = viewModel::onQueryChange,
            stores = stores,
            onStoreClick = { directions.toStore(it.id) },
            modifier = Modifier.padding(innerPadding),
            header = {
                item(key = OTHER_CARD_KEY) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.other_card)) },
                        supportingContent = { Text(stringResource(R.string.other_card_hint)) },
                        leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = directions.toCustomStore),
                    )
                }
                // Each section's stores are also in the full list below, so their keys mustn't be the store id alone.
                if (suggested.isNotEmpty()) {
                    item(key = SUGGESTED_KEY) { SectionHeading(stringResource(R.string.suggested_stores)) }
                    items(suggested, key = { "$SUGGESTED_KEY/${it.id}" }) { store ->
                        StoreRow(store, onClick = { directions.toStore(store.id) }, trailing = trailing(store))
                    }
                }
                if (frequentlyAdded.isNotEmpty()) {
                    item(key = FREQUENTLY_ADDED_KEY) { SectionHeading(stringResource(R.string.frequently_added_stores)) }
                    items(frequentlyAdded, key = { "$FREQUENTLY_ADDED_KEY/${it.id}" }) { store ->
                        StoreRow(store, onClick = { directions.toStore(store.id) }, trailing = trailing(store))
                    }
                }
                if (suggested.isNotEmpty() || frequentlyAdded.isNotEmpty()) {
                    item(key = ALL_STORES_KEY) { SectionHeading(stringResource(R.string.all_stores)) }
                }
            },
            trailing = trailing,
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.l, top = Spacing.l, end = Spacing.l, bottom = Spacing.s).semantics { heading() },
    )
}
