package io.issac.pleosandroid.feature.badges

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme

@Preview(name = "D05 · Card detail", widthDp = 1120, heightDp = 720)
@Composable
private fun MilestoneDetailPreview() {
    PleosTheme {
        CardDetailScreen(
            card = SampleCollection.cards.first { it.kind == CardKind.Place },
            onBack = {},
            modifier = Modifier.width(1120.dp).height(720.dp),
        )
    }
}

@Preview(name = "D05 · Card detail compact", widthDp = 440, heightDp = 760)
@Composable
private fun MilestoneDetailCompactPreview() {
    PleosTheme {
        CardDetailScreen(
            card = SampleCollection.cards.first { it.kind == CardKind.Safety },
            onBack = {},
            modifier = Modifier.width(440.dp).height(760.dp),
        )
    }
}
