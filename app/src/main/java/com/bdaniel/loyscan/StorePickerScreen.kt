package com.bdaniel.loyscan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bdaniel.loyscan.ui.theme.icons.Icons
import com.bdaniel.loyscan.ui.toBrandColorOrNull

const val STORE_SEARCH_TAG = "storeSearch"

// Not a store id: catalogue ids are slugs and custom ones UUIDs.
private const val OTHER_CARD_KEY = "other card"

/** First step of adding a card: pick the store, searching by name or alias, or "Other card" for one that isn't listed. */
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
        Column(Modifier.padding(innerPadding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag(STORE_SEARCH_TAG),
                placeholder = { Text("Search stores") },
                singleLine = true,
            )
            if (stores.isEmpty() && query.isNotBlank()) {
                Text("No store matches “${query.trim()}”", modifier = Modifier.padding(16.dp))
            }
            LazyColumn {
                item(key = OTHER_CARD_KEY) {
                    ListItem(
                        headlineContent = { Text("Other card") },
                        supportingContent = { Text("A store that isn't listed") },
                        leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = directions.toCustomStore),
                    )
                }
                items(stores, key = { it.id }) { store ->
                    ListItem(
                        headlineContent = { Text(store.name) },
                        leadingContent = {
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(store.color.toBrandColorOrNull() ?: MaterialTheme.colorScheme.surfaceVariant)
                            )
                        },
                        modifier = Modifier.clickable { directions.toStore(store.id) },
                    )
                }
            }
        }
    }
}
