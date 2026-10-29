package com.bdaniel.loyscan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bdaniel.loyscan.ui.theme.icons.Icons

// Not a store id: catalogue ids are slugs and custom ones UUIDs.
private const val OTHER_CARD_KEY = "other card"

/**
 * First step of adding a card: pick the store, searching by name or alias, or "Other card" for one that isn't listed.
 * A custom store has an Edit button (#53).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorePickerScreen(
    directions: StorePickerDirections,
    viewModel: StorePickerViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val stores by viewModel.stores.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose store") },
                navigationIcon = {
                    IconButton(onClick = directions.toBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                        headlineContent = { Text("Other card") },
                        supportingContent = { Text("A store that isn't listed") },
                        leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = directions.toCustomStore),
                    )
                }
            },
            trailing = { store ->
                if (store.isCustom) {
                    {
                        IconButton(onClick = { directions.toEditStore(store.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit ${store.name}")
                        }
                    }
                } else {
                    null
                }
            },
        )
    }
}
