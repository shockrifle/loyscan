package com.bdaniel.loyscan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bdaniel.loyscan.ui.theme.Spacing
import com.bdaniel.loyscan.domain.Store
import com.bdaniel.loyscan.domain.search
import com.bdaniel.loyscan.ui.theme.icons.Icons
import com.bdaniel.loyscan.ui.toBrandColorOrNull

const val STORE_SEARCH_TAG = "storeSearch"

/**
 * A search field over stores and the [stores] it matches, each tapped to [onStoreClick]: the store picker's body, and
 * the card form's [StoreChooser]. [header] items come first whatever the search, and [trailing] adds a store's action.
 */
@Composable
fun StoreSearchList(
    query: String,
    onQueryChange: (String) -> Unit,
    stores: List<Store>,
    onStoreClick: (Store) -> Unit,
    modifier: Modifier = Modifier,
    header: LazyListScope.() -> Unit = {},
    trailing: (Store) -> (@Composable () -> Unit)? = { null },
) {
    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s).testTag(STORE_SEARCH_TAG),
            placeholder = { Text(stringResource(R.string.search_stores)) },
            singleLine = true,
        )
        if (stores.isEmpty() && query.isNotBlank()) {
            Text(stringResource(R.string.no_store_matches, query.trim()), modifier = Modifier.padding(Spacing.l))
        }
        LazyColumn {
            header()
            items(stores, key = { it.id }) { store ->
                StoreRow(store, onClick = { onStoreClick(store) }, trailing = trailing(store))
            }
        }
    }
}

/** One store in a list: its colour dot and name, with an optional [trailing] action. */
@Composable
fun StoreRow(store: Store, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(store.name) },
        leadingContent = { StoreDot(store, STORE_DOT_SMALL) },
        trailingContent = trailing,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * Moves an edited card to another of [stores] (#8): a full-screen dialog over the card form, searched as the store
 * picker is. Choosing a store or closing it calls [onChoose] or [onDismiss]; the caller hides it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreChooser(
    stores: List<Store>,
    onChoose: (Store) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matching = remember(stores, query) { stores.search(query) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.choose_store)) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                            }
                        },
                    )
                },
            ) { innerPadding ->
                StoreSearchList(
                    query = query,
                    onQueryChange = { query = it },
                    stores = matching,
                    onStoreClick = onChoose,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

/** A store's colour dot in a list row. */
val STORE_DOT_SMALL = 24.dp

/** A store's colour dot beside its name as a heading (the card form). */
val STORE_DOT_LARGE = 32.dp

/** The store's brand colour as a dot, or the surface variant when it has none. */
@Composable
fun StoreDot(store: Store, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(store.color.toBrandColorOrNull() ?: MaterialTheme.colorScheme.surfaceVariant)
    )
}
