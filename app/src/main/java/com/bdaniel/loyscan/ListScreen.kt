package com.bdaniel.loyscan

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.bdaniel.loyscan.domain.CardSort
import com.bdaniel.loyscan.domain.LoyaltyCard
import com.bdaniel.loyscan.ui.theme.icons.Icons
import com.bdaniel.loyscan.ui.contrastingTextColor
import com.bdaniel.loyscan.ui.toBrandColorOrNull

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.ListScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    directions: ListDirections,
    viewModel: ListViewModel = hiltViewModel()
) {
    val cards by viewModel.cards.collectAsState()
    val query by viewModel.query.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val deletion by viewModel.deletion.collectAsState()
    val selecting = selection.isNotEmpty()
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    // The dialog belongs to this selection: once it ends (deleted, or cleared), the next one doesn't open it, nor show
    // a failure left from it.
    LaunchedEffect(selecting) {
        if (!selecting) {
            confirmingDelete = false
            viewModel.dismissDeletion()
        }
    }
    // Back ends the selection before it leaves the list.
    BackHandler(enabled = selecting, onBack = viewModel::clearSelection)
    val gridState = rememberLazyGridState()
    var openedTile by rememberSaveable(stateSaver = OpenedTile.Saver) { mutableStateOf<OpenedTile?>(null) }
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val screen = "${windowSize.width}x${windowSize.height}@${density.density}x${density.fontScale}"

    // Opening a card counts a use, which can re-sort the list (#61), before or during the return. Until the list is
    // back, the opened card's tile is put where it was tapped (or as near as the list allows), so the card shrinks back
    // into it rather than into a tile that moved away.
    openedTile?.let { tile ->
        val index = cards?.indexOfFirst { it.id == tile.cardId } ?: -1
        // After a rotation or a display size change, the old offset in pixels can be below the grid: at the top, it is in view.
        val offset = if (tile.screen == screen) tile.offset else 0
        // Safe in composition, and applied at the next measure instead of keeping the first visible tile in place.
        if (index >= 0) SideEffect { gridState.requestScrollToItem(index, scrollOffset = -offset) }
    }
    val transition = animatedVisibilityScope.transition
    val settled = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible
    // Set by a tap that opened a card, so this runs only once the list has left and come back.
    LaunchedEffect(settled) { if (settled) openedTile = null }

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionBar(
                    count = selection.size,
                    onClose = viewModel::clearSelection,
                    // Only one card can be edited at a time.
                    onEdit = cards?.singleOrNull { it.id in selection }?.takeIf { selection.size == 1 }?.let { card ->
                        {
                            directions.toEditCard(card.id, card.store.id)
                            viewModel.clearSelection()
                        }
                    },
                    onDelete = { confirmingDelete = true },
                )
            } else TopAppBar(
                title = { Text("My Cards") },
                actions = {
                    SortMenu(sort = sort, onSortChange = viewModel::onSortChange)
                    IconButton(onClick = { /* TODO: Handle settings click */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!selecting) {
                FloatingActionButton(onClick = directions.toAddCard) {
                    Icon(Icons.Default.Add, contentDescription = "Add card")
                }
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        // Loaded, no cards, and no search to blame: there is nothing to search yet.
        if (cards?.isEmpty() == true && query.isBlank()) {
            EmptyCardList(onAddCard = directions.toAddCard, modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }
        Column(Modifier.padding(innerPadding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag(CARD_SEARCH_TAG),
                placeholder = { Text("Search cards") },
                singleLine = true,
            )
            if (cards?.isEmpty() == true) {
                Text("No card matches “${query.trim()}”", modifier = Modifier.padding(16.dp))
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                // Room below the last row for the floating "Add card" button (56dp + its 16dp margin, and a gap).
                contentPadding = PaddingValues(bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cards.orEmpty(), key = { it.id }) { card ->
                    CardItem(
                        animatedVisibilityScope = animatedVisibilityScope,
                        card = card,
                        selected = card.id in selection,
                        onLongClick = { viewModel.toggleSelection(card.id) },
                        onClick = onClick@{
                            // While cards are selected, a tap selects or deselects one instead of opening it.
                            if (selecting) return@onClick viewModel.toggleSelection(card.id)
                            val offset = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == card.id }?.offset?.y
                            // A second tile tapped while the list leaves opens nothing, so it isn't the opened one (#69).
                            if (directions.toCard(card.id, card.store.color)) openedTile = OpenedTile(card.id, offset ?: 0, screen)
                        },
                    )
                }
            }
        }
        if (confirmingDelete && selecting) {
            ConfirmDelete(
                count = selection.size,
                failed = deletion == Deletion.FAILED,
                deleting = deletion == Deletion.DELETING,
                onConfirm = viewModel::deleteSelection,
                onDismiss = {
                    confirmingDelete = false
                    viewModel.dismissDeletion()
                },
            )
        }
    }
}

/** The top bar while cards are selected: how many, and what can be done with them. [onEdit] is null for several. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onEdit: (() -> Unit)?, onDelete: () -> Unit) {
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        },
        actions = {
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        },
    )
}

/**
 * Asks before deleting the [count] selected cards. A failed delete is said here, and "Delete" tries again. While
 * [deleting], the dialog stays: closing it wouldn't stop the write.
 */
@Composable
private fun ConfirmDelete(count: Int, failed: Boolean, deleting: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val one = count == 1
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text(if (one) "Delete this card?" else "Delete $count cards?") },
        text = {
            Text(
                when {
                    failed && one -> "The card couldn't be deleted. Try again."
                    failed -> "The cards couldn't be deleted. Try again."
                    one -> "It is removed from this phone for good."
                    else -> "They are removed from this phone for good."
                },
                color = if (failed) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !deleting) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !deleting) { Text("Cancel") } },
    )
}

/**
 * The tile of the card the list opened, [offset] pixels below the top of the grid when it was tapped on [screen] (its
 * window size, density and font scale).
 */
private data class OpenedTile(val cardId: String, val offset: Int, val screen: String) {
    companion object {
        val Saver = listSaver<OpenedTile?, Any>(
            save = { tile -> if (tile == null) emptyList() else listOf(tile.cardId, tile.offset, tile.screen) },
            restore = { saved -> OpenedTile(saved[0] as String, saved[1] as Int, saved[2] as String) },
        )
    }
}

/** What the list shows before the first card is added. */
@Composable
private fun EmptyCardList(onAddCard: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No cards yet", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Add a loyalty card by scanning its barcode or typing its number.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAddCard) {
            Text("Add your first card")
        }
    }
}

private val SORT_LABELS = mapOf(
    CardSort.NAME to "Name",
    CardSort.MOST_USED to "Most used",
    CardSort.RECENTLY_USED to "Recently used",
)

/** The Sort button and its menu; the current [sort] is marked. */
@Composable
private fun SortMenu(sort: CardSort, onSortChange: (CardSort) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Sort, contentDescription = "Sort")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CardSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(SORT_LABELS.getValue(option)) },
                    leadingIcon = { RadioButton(selected = option == sort, onClick = null) },
                    onClick = {
                        expanded = false
                        onSortChange(option)
                    },
                    modifier = Modifier.semantics { selected = option == sort },
                )
            }
        }
    }
}

const val CARD_SEARCH_TAG = "cardSearch"
const val CARD_TILE_TAG = "cardTile"

/** The shared-element key that pairs a card's tile with its detail screen ([DetailScreen]). */
fun cardTransitionKey(cardId: String) = "card/$cardId"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.CardItem(
    animatedVisibilityScope: AnimatedVisibilityScope,
    card: LoyaltyCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: () -> Unit = {},
) {
    val cardColor = card.store.color.toBrandColorOrNull() ?: MaterialTheme.colorScheme.surfaceVariant
    val textColor = cardColor.contrastingTextColor()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Select"),
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardColor)
                    .aspectRatio(ID1_CARD_ASPECT_RATIO)
                    .testTag(CARD_TILE_TAG)
                    // TalkBack reads a merged node's description in place of its text: the store, then the label.
                    .semantics { contentDescription = listOfNotNull(card.store.name, card.label).joinToString(", ") }
                    .sharedElement(
                        sharedContentState = rememberSharedContentState(key = cardTransitionKey(card.id)),
                        animatedVisibilityScope = animatedVisibilityScope
                    ),
            ) {
                // The initials take the room the label leaves, shrinking to fit it at large font scales.
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    if (!card.store.logo.isNullOrBlank()) {
                        AsyncImage(
                            model = card.store.logo,
                            contentDescription = null, // The tile is described as a whole.
                            modifier = Modifier
                                .fillMaxSize(0.6f)
                        )
                    } else {
                        Text(
                            text = card.store.initials,
                            style = MaterialTheme.typography.headlineLarge,
                            color = textColor,
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(maxFontSize = MaterialTheme.typography.headlineLarge.fontSize),
                        )
                    }
                }
                Text(
                    text = card.label ?: card.store.name,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null, // said by the tile's selected state
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        }
    }
}

/** Width to height of an ID-1 (bank) card, 85.60 × 53.98 mm. */
private const val ID1_CARD_ASPECT_RATIO = 85.60f / 53.98f
