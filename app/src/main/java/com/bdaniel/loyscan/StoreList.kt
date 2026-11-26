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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import com.bdaniel.loyscan.ui.StoreLogoImage
import com.bdaniel.loyscan.ui.contrastingTextColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
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

/** A store row's badge showing the store's logo (#186). */
const val STORE_BADGE_LOGO_TAG = "storeBadgeLogo"

/** A store row's badge showing the store's initials, when it has no logo or the logo can't be shown (#186). */
const val STORE_BADGE_INITIALS_TAG = "storeBadgeInitials"

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

/** One store in a list: its [StoreBadge] and name, with an optional [trailing] action. */
@Composable
fun StoreRow(store: Store, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(store.name) },
        leadingContent = { StoreBadge(store) },
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

/** A store row's badge (#186): a circle the size of a Material list row's avatar. */
val STORE_BADGE_SIZE = 40.dp

/** The logo's share of the badge: a square logo at 70% fits inside the circle (1/√2 ≈ 0.71). */
private const val BADGE_LOGO_FRACTION = 0.7f

/** The smallest the badge's initials shrink to, so two wide letters fit at large font scales. */
private val BADGE_INITIALS_MIN_SIZE = 8.sp

/**
 * The store in a list row (#186): its brand colour as a circle, with the store's logo fitted inside, else its initials,
 * as the My cards tile shows a store. A logo that can't be loaded, or whose file is missing, shows the initials; while
 * it loads, only the colour shows. The badge adds nothing to TalkBack: the row reads as the store's name.
 */
@Composable
fun StoreBadge(store: Store) {
    val color = store.color.toBrandColorOrNull() ?: MaterialTheme.colorScheme.surfaceVariant
    var logoFailed by remember(store.logo) { mutableStateOf(false) }
    val logo = store.logo?.takeUnless { logoFailed }
    Box(
        Modifier.size(STORE_BADGE_SIZE).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            StoreLogoImage( // TalkBack: the row is read as the store's name.
                logo = logo,
                treatment = store.logoTreatment,
                brandColor = store.color,
                onError = { logoFailed = true },
                modifier = Modifier.fillMaxSize(BADGE_LOGO_FRACTION).testTag(STORE_BADGE_LOGO_TAG),
            )
        } else {
            // Kept out of the row's merged text, so TalkBack reads the store's name only.
            Box(Modifier.clearAndSetSemantics { testTag = STORE_BADGE_INITIALS_TAG }) {
                Text(
                    text = store.initials,
                    color = color.contrastingTextColor(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = BADGE_INITIALS_MIN_SIZE,
                        maxFontSize = MaterialTheme.typography.titleMedium.fontSize,
                    ),
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }
        }
    }
}

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
