package io.issac.pleosandroid.feature.badges

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.MilestoneCard
import io.issac.pleosandroid.core.designsystem.theme.PleosSpacing

@Composable
internal fun CardDetailScreen(card: CollectionCard, onBack: () -> Unit, modifier: Modifier = Modifier) {
    CollectionPage(
        title = stringResource(R.string.card_detail_collection),
        onBack = onBack,
        modifier = modifier,
    ) {
        BoxWithConstraints(Modifier.widthIn(max = 1040.dp).fillMaxWidth()) {
            val face = card.face()
            if (maxWidth >= 760.dp) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PleosSpacing.large),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MilestoneCard(face = face, modifier = Modifier.width(300.dp))
                    CardDetailFacts(card = card, modifier = Modifier.weight(1f))
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PleosSpacing.large),
                ) {
                    MilestoneCard(face = face, modifier = Modifier.width(240.dp))
                    CardDetailFacts(card = card, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun CardDetailFacts(card: CollectionCard, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = CollectionColors.Surface,
        contentColor = CollectionColors.Text,
        border = BorderStroke(1.dp, CollectionColors.Border),
    ) {
        Column(
            modifier = Modifier.padding(PleosSpacing.large),
            verticalArrangement = Arrangement.spacedBy(PleosSpacing.medium),
        ) {
            Text(
                text = stringResource(
                    when (card.kind) {
                        CardKind.Place -> R.string.card_detail_place
                        CardKind.Safety -> R.string.card_detail_safety
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            if (card.featured) {
                Text(
                    text = stringResource(R.string.collection_featured),
                    style = MaterialTheme.typography.labelLarge,
                    color = CollectionColors.Accent,
                )
            }
            Text(
                text = card.description,
                style = MaterialTheme.typography.bodyLarge,
            )
            HorizontalDivider(color = CollectionColors.Border)
            Column(verticalArrangement = Arrangement.spacedBy(PleosSpacing.small)) {
                Text(
                    text = stringResource(R.string.collection_first_collected),
                    style = MaterialTheme.typography.labelLarge,
                    color = CollectionColors.Muted,
                )
                Text(
                    text = card.firstCollectedOn,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(PleosSpacing.small)) {
                Text(
                    text = card.memorySummary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = card.latestMemory,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CollectionColors.Muted,
                )
            }
            UnavailableDetailAction(stringResource(R.string.card_detail_memory))
            UnavailableDetailAction(stringResource(R.string.card_detail_change_featured))
        }
    }
}

@Composable
private fun UnavailableDetailAction(label: String) {
    OutlinedButton(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        enabled = false,
        colors = ButtonDefaults.outlinedButtonColors(
            disabledContentColor = CollectionColors.Muted,
        ),
        border = BorderStroke(1.dp, CollectionColors.Border),
    ) {
        Text(label)
    }
}
