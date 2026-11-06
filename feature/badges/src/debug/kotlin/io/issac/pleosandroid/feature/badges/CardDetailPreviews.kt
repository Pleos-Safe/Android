package io.issac.pleosandroid.feature.badges

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.feature.badges.detail.CardDetailScreen
import io.issac.pleosandroid.feature.badges.model.CollectionSeed

@Preview(name = "D15 · 소장 상세", widthDp = 1216, heightDp = 680)
@Preview(name = "D15 · 분할 상세", widthDp = 520, heightDp = 616)
@Composable
private fun CardDetailPreview() {
    PleosTheme { CardDetailScreen(CollectionSeed.cards[1], false, {}, {}) }
}
