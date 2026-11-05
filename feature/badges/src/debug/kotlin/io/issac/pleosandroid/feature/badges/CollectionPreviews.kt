package io.issac.pleosandroid.feature.badges

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme

@Preview(name = "D01 · Collection", widthDp = 1000, heightDp = 620)
@Composable
private fun MilestoneCollectionPreview() {
    PleosTheme {
        Box(Modifier.width(1000.dp)) {
            CollectionScreen(SampleCollection.cards, null, {}, {})
        }
    }
}

@Preview(name = "D01 · Place filter", widthDp = 440, heightDp = 680)
@Composable
private fun MilestoneCollectionCompactPreview() {
    PleosTheme {
        Box(Modifier.width(440.dp)) {
            CollectionScreen(SampleCollection.cards, CardKind.Place, {}, {})
        }
    }
}
