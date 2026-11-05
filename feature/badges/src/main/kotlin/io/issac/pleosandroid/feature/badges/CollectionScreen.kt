package io.issac.pleosandroid.feature.badges

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.MilestoneCard

@Composable
internal fun CollectionScreen(
    cards: List<CollectionCard>,
    filter: CardKind?,
    onFilterChange: (CardKind?) -> Unit,
    onBack: () -> Unit,
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CollectionPage(title = stringResource(R.string.collection_title), onBack = onBack, modifier = modifier) {
        Text(
            text = stringResource(R.string.collection_card_count, cards.size),
            style = MaterialTheme.typography.titleMedium,
            color = CollectionColors.Muted,
        )
        CollectionFilters(filter = filter, onFilterChange = onFilterChange)
        val visibleCards = if (filter == null) cards else cards.filter { it.kind == filter }
        if (visibleCards.isEmpty()) {
            Text(stringResource(R.string.collection_no_cards), color = CollectionColors.Muted)
        } else {
            CollectionShelf(cards = visibleCards, onCardClick = onCardClick)
        }
        Text(
            text = stringResource(R.string.collection_sample),
            style = MaterialTheme.typography.labelMedium,
            color = CollectionColors.Muted,
        )
    }
}

@Composable
private fun CollectionFilters(filter: CardKind?, onFilterChange: (CardKind?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            null to R.string.collection_all,
            CardKind.Place to R.string.collection_places,
            CardKind.Safety to R.string.collection_safety,
        ).forEach { (kind, label) ->
            FilterChip(
                selected = filter == kind,
                onClick = { onFilterChange(kind) },
                label = { Text(stringResource(label)) },
                colors = FilterChipDefaults.filterChipColors(
                    labelColor = CollectionColors.Muted,
                    selectedContainerColor = CollectionColors.Accent,
                    selectedLabelColor = CollectionColors.Background,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = filter == kind,
                    borderColor = CollectionColors.Border,
                ),
            )
        }
    }
}

@Composable
private fun CollectionShelf(cards: List<CollectionCard>, onCardClick: (String) -> Unit) {
    BoxWithConstraints(Modifier.widthIn(max = 1040.dp).fillMaxWidth()) {
        val columns = if (maxWidth >= 544.dp) 2 else 1
        val itemWidth = (maxWidth - 16.dp * (columns - 1)) / columns
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            cards.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { card ->
                        CollectionShelfCard(
                            card = card,
                            onClick = { onCardClick(card.id) },
                            modifier = Modifier.width(itemWidth),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionShelfCard(
    card: CollectionCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLabel = stringResource(R.string.collection_open_card)
    val openCardAction = stringResource(R.string.collection_open_named_card, card.title)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = CollectionColors.Surface,
        border = BorderStroke(1.dp, CollectionColors.Border),
    ) {
        Column(
            modifier = Modifier
                .testTag("collection-card-${card.id}")
                .clickable(role = Role.Button, onClickLabel = openCardAction, onClick = onClick)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.heightIn(min = 20.dp)) {
                if (card.featured) {
                    Text(
                        stringResource(R.string.collection_featured),
                        style = MaterialTheme.typography.labelLarge,
                        color = CollectionColors.Accent,
                    )
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                MilestoneCard(card.face(), Modifier.width(192.dp))
            }
            Text(card.memorySummary, color = CollectionColors.Text, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.collection_first_collected) + " · " + card.firstCollectedOn,
                color = CollectionColors.Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(openLabel, color = CollectionColors.Accent, style = MaterialTheme.typography.labelLarge)
        }
    }
}
