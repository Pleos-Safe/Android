package io.issac.pleosandroid.core.cardui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.cardui.print.CardPrintView
import io.issac.pleosandroid.core.designsystem.component.PleosChip
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.component.pleosShellBackground
import io.issac.pleosandroid.core.designsystem.modifier.customClickable
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.domain.cards.CopyStatus
import io.issac.pleosandroid.core.domain.cards.PackType

@Composable
fun CardShelfGrid(
    cards: List<CardCopy>,
    featuredId: String?,
    pendingIds: Set<String>,
    title: String,
    sectionTitle: String,
    onCard: (CardCopy) -> Unit,
    onPreviewMotions: () -> Unit,
    modeControl: @Composable () -> Unit,
    caption: (CardCopy) -> String,
    modifier: Modifier = Modifier,
    balance: String? = null,
    focusId: String? = null,
    onFocused: (String) -> Unit = {},
    inboxAction: (@Composable () -> Unit)? = null,
    openEmptyInbox: (() -> Unit)? = null,
) {
    var region by rememberSaveable { mutableStateOf<PackType?>(null) }
    var traces by rememberSaveable { mutableStateOf(false) }
    var highlighted by rememberSaveable { mutableStateOf<String?>(null) }
    val grid = rememberLazyGridState()
    val filtered = remember(cards, region, traces) { cards.matching(region, traces) }
    val headerCount = if (inboxAction == null) 3 else 4
    LaunchedEffect(focusId, cards) {
        val target = cards.find { it.id == focusId } ?: return@LaunchedEffect
        region = target.print.packType
        traces = target.status == CopyStatus.Trace
        highlighted = target.id
        val slot = cards.matching(region, traces).indexOf(target)
        withFrameNanos { }
        grid.scrollToItem(headerCount + slot)
        onFocused(target.id)
    }
    BoxWithConstraints(modifier.fillMaxSize().pleosShellBackground()) {
        val wide = maxWidth >= PleosLayout.twoPaneMinWidth
        val columns = when {
            wide -> 4
            maxWidth >= 600.dp * PleosTextStyles.scale -> 3
            maxWidth >= 365.dp * PleosTextStyles.scale -> 2
            else -> 1
        }
        val gap = PleosLayout.itemGap
        LazyVerticalGrid(
            GridCells.Fixed(columns),
            state = grid,
            modifier = Modifier.testTag("album-grid"),
            contentPadding = PaddingValues(horizontal = PleosLayout.horizontalPadding(wide), vertical = gap),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val previewLink: @Composable () -> Unit = {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        modeControl()
                        PleosTextLink("연출 모아보기", onPreviewMotions, Modifier.testTag("open-motion-gallery"))
                    }
                }
                if (wide) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        Box(Modifier.weight(1f)) { AlbumHeading(title, wide, balance) }
                        previewLink()
                    }
                } else {
                    Column {
                        AlbumHeading(title, wide, balance)
                        previewLink()
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PleosChip("전체", !traces && region == null, {
                        traces = false
                        region = null
                    })
                    for (r in PackType.entries) {
                        PleosChip(r.title, !traces && region == r, {
                            traces = false
                            region = r
                        })
                    }
                    PleosChip("흔적 ${cards.count { it.status == CopyStatus.Trace }}", traces, {
                        traces = true
                        region = null
                    })
                }
            }
            if (inboxAction != null) {
                item(span = { GridItemSpan(maxLineSpan) }) { inboxAction() }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val title = if (traces) "복구할 흔적" else sectionTitle
                SectionTitle("$title ${filtered.size}장")
            }
            if (filtered.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AlbumPanel {
                        BodyText(
                            if (traces) {
                                "복구를 기다리는 흔적이 없어요."
                            } else if (region != null) {
                                "이 지역에서 모은 카드가 없어요."
                            } else {
                                "첫 팩을 열어 컬렉션을 시작하세요."
                            },
                        )
                        if (openEmptyInbox != null) PleosTextLink("팩 보관함 열기", openEmptyInbox)
                    }
                }
            }
            items(filtered, key = { it.id }) { card ->
                Column(
                    Modifier.fillMaxWidth().testTag("album-copy-" + card.id).customClickable(onClick = {
                        onCard(card)
                    }),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    VaultStage(Modifier.fillMaxWidth().aspectRatio(.86f), highlighted = true) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CardPrintView(card, Modifier.fillMaxHeight(.90f).aspectRatio(2f / 3f))
                        }
                    }
                    val badge = when (card.id) {
                        in pendingIds -> "강화 결과 확인"
                        featuredId -> "대표 카드"
                        highlighted -> "방금 확인한 카드"
                        else -> null
                    }
                    if (badge != null) Caption(badge, accent = true)
                    BodyText("${card.print.title}  +${card.level}")
                    Caption(
                        if (traces) {
                            "복구 대기 · ${card.grade}"
                        } else {
                            caption(card)
                        },
                    )
                }
            }
        }
    }
}

private fun List<CardCopy>.matching(region: PackType?, traces: Boolean): List<CardCopy> = filter { card ->
    (card.status == CopyStatus.Trace) == traces && (region == null || card.print.packType == region)
}
