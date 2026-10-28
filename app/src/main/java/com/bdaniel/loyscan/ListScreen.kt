package com.bdaniel.loyscan

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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

    Scaffold(
        topBar = {
            TopAppBar(
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
            FloatingActionButton(onClick = directions.toAddCard) {
                Icon(Icons.Default.Add, contentDescription = "Add card")
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
                // Room below the last row for the floating "Add card" button (56dp + its 16dp margin, and a gap).
                contentPadding = PaddingValues(bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cards.orEmpty(), key = { it.id }) { card ->
                    CardItem(
                        animatedVisibilityScope = animatedVisibilityScope,
                        card = card,
                        onClick = { directions.toCard(card.id, card.store.color) },
                    )
                }
            }
        }
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
    modifier: Modifier = Modifier
) {
    val cardColor = card.store.color.toBrandColorOrNull() ?: MaterialTheme.colorScheme.surfaceVariant
    val textColor = cardColor.contrastingTextColor()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardColor)
                .aspectRatio(ID1_CARD_ASPECT_RATIO)
                .testTag(CARD_TILE_TAG)
                .sharedElement(
                    sharedContentState = rememberSharedContentState(key = cardTransitionKey(card.id)),
                    animatedVisibilityScope = animatedVisibilityScope
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!card.store.logo.isNullOrBlank()) {
                AsyncImage(
                    model = card.store.logo,
                    contentDescription = card.store.name,
                    modifier = Modifier
                        .fillMaxSize(0.6f)
                )
            } else {
                Text(
                    text = card.store.initials,
                    // Read out as the store's name, not letter by letter.
                    modifier = Modifier.semantics { contentDescription = card.store.name },
                    style = MaterialTheme.typography.headlineLarge,
                    color = textColor,
                )
            }
            Text(
                text = card.label ?: card.store.name,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Width to height of an ID-1 (bank) card, 85.60 × 53.98 mm. */
private const val ID1_CARD_ASPECT_RATIO = 85.60f / 53.98f
