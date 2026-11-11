package io.issac.pleosandroid.feature.forge

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.cardui.component.CardShelfGrid
import io.issac.pleosandroid.core.domain.cards.AlbumSnapshot
import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.domain.cards.CardRules
import io.issac.pleosandroid.core.domain.points.formatHalfPoints

@Composable
internal fun ForgeSelectionScreen(
    album: AlbumSnapshot,
    onCard: (CardCopy) -> Unit,
    openGallery: () -> Unit,
    openInbox: () -> Unit,
    modeControl: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardShelfGrid(
        cards = album.collection,
        featuredId = album.featuredId,
        pendingIds = album.pending.keys,
        title = "강화할 카드 선택",
        sectionTitle = "강화할 수 있는 카드",
        balance = formatHalfPoints(album.balanceHalfPoints),
        onCard = onCard,
        onPreviewMotions = openGallery,
        modeControl = modeControl,
        caption = { card -> "${card.grade} · 1회 ${formatHalfPoints(CardRules.cost(card.grade, card.level))}" },
        openEmptyInbox = openInbox.takeIf { album.inbox.isNotEmpty() },
        modifier = modifier,
    )
}
