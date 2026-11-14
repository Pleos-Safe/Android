package io.issac.pleosandroid.feature.collection.shelf

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.issac.pleosandroid.core.cardui.component.CardShelfGrid
import io.issac.pleosandroid.core.cardui.component.dateOf
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosButtonStyle
import io.issac.pleosandroid.core.domain.cards.AlbumSnapshot
import io.issac.pleosandroid.core.domain.cards.CardCopy

@Composable
internal fun CollectionShelfScreen(
    album: AlbumSnapshot,
    focusId: String?,
    onFocused: (String) -> Unit,
    onCard: (CardCopy) -> Unit,
    openInbox: () -> Unit,
    openGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardShelfGrid(
        cards = album.collection,
        featuredId = album.featuredId,
        pendingIds = album.pending.keys,
        title = "컬렉션",
        sectionTitle = "보유 카드",
        onCard = onCard,
        onPreviewMotions = openGallery,
        caption = { card ->
            "${dateOf(card.acquiredAt)} 획득 · ${card.grade}" + if (card.variant) " · 변종" else ""
        },
        focusId = focusId,
        onFocused = onFocused,
        inboxAction = {
            PleosButton(
                "팩 보관함 · ${album.inbox.size}개",
                openInbox,
                Modifier.fillMaxWidth().testTag("album-open-inbox"),
                style = PleosButtonStyle.Secondary,
                showArrow = true,
            )
        },
        openEmptyInbox = openInbox.takeIf { album.inbox.isNotEmpty() },
        modifier = modifier,
    )
}
