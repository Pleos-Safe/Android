package io.issac.pleosandroid.feature.badges

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.feature.badges.collection.CollectionScreen
import io.issac.pleosandroid.feature.badges.model.CollectionCardDesign
import io.issac.pleosandroid.feature.badges.model.CollectionSeed
import io.issac.pleosandroid.feature.badges.model.Haeundae
import io.issac.pleosandroid.feature.badges.model.SafeHome

@Preview(name = "D11 · 전체 선반", widthDp = 1216, heightDp = 680)
@Preview(name = "D11 · 분할 선반", widthDp = 520, heightDp = 616)
@Composable
private fun CollectionPreview() {
    PleosTheme { CollectionScreen(CollectionSeed.cards, CollectionSeed.representativeId, {}) }
}

@Preview(name = "D04 · 첫 카드 전", widthDp = 520, heightDp = 616)
@Composable
private fun EmptyCollectionPreview() {
    PleosTheme { CollectionScreen(emptyList(), null, {}) }
}

@Preview(name = "D01F · 첫 소장", widthDp = 1216, heightDp = 680)
@Composable
private fun FirstCollectionPreview() {
    val card = CollectionSeed.cards.first().let {
        it.copy(earnedLevel = 1, revealedLevel = 1, memories = it.memories.take(1))
    }
    PleosTheme { CollectionScreen(listOf(card), null, {}) }
}

@Preview(name = "D01 · 두 장 소장", widthDp = 1216, heightDp = 680)
@Composable
private fun TwoCardCollectionPreview() {
    val cards = CollectionSeed.cards.filter {
        it.design == CollectionCardDesign.Haeundae ||
            it.design == CollectionCardDesign.SafeHome
    }
        .map { it.copy(revealedLevel = it.earnedLevel) }
    PleosTheme { CollectionScreen(cards, CollectionSeed.representativeId, {}) }
}
