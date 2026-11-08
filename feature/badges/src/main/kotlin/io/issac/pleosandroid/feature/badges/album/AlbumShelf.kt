package io.issac.pleosandroid.feature.badges.album

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.R as DesignR
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosButtonStyle
import io.issac.pleosandroid.core.designsystem.component.PleosChip
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.component.pleosShellBackground
import io.issac.pleosandroid.core.designsystem.modifier.customClickable
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.cards.AlbumSnapshot
import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.domain.cards.CardRules
import io.issac.pleosandroid.core.domain.cards.CopyStatus
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.cards.ReceivedPack
import io.issac.pleosandroid.feature.badges.component.Caption
import io.issac.pleosandroid.feature.badges.component.SectionTitle
import io.issac.pleosandroid.feature.badges.component.VaultStage

@Composable
internal fun AlbumShelf(
    album: AlbumSnapshot,
    forge: Boolean,
    focusId: String?,
    onFocused: (String) -> Unit,
    packShelf: () -> Unit,
    onPreviewMotions: () -> Unit,
    modeControl: @Composable () -> Unit = {},
    onCard: (CardCopy) -> Unit,
    modifier: Modifier = Modifier,
) {
    var region by rememberSaveable { mutableStateOf<PackType?>(null) }
    var traces by rememberSaveable { mutableStateOf(false) }
    var highlighted by rememberSaveable { mutableStateOf<String?>(null) }
    val grid = rememberLazyGridState()
    val collection = album.collection
    val filtered = collection.filter {
        (it.status == CopyStatus.Trace) == traces &&
            (region == null || it.print.packType == region)
    }
    val headerCount = if (forge) 3 else 4
    LaunchedEffect(focusId, album) {
        val target = collection.find { it.id == focusId } ?: return@LaunchedEffect
        region = target.print.packType
        traces = target.status == CopyStatus.Trace
        highlighted = target.id
        val slot = collection.filter {
            (it.status == CopyStatus.Trace) == traces &&
                (region == null || it.print.packType == region)
        }.indexOf(target)
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
                val title = if (forge) "강화할 카드 선택" else "컬렉션"
                val balance = if (forge) points(album.balanceHalfPoints) else null
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
                    PleosChip("흔적 ${collection.count { it.status == CopyStatus.Trace }}", traces, {
                        traces = true
                        region = null
                    })
                }
            }
            if (!forge) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PleosButton(
                        "팩 보관함 · ${album.inbox.size}개",
                        packShelf,
                        Modifier.fillMaxWidth().testTag("album-open-inbox"),
                        style = PleosButtonStyle.Secondary,
                        showArrow = true,
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val title = when {
                    traces -> "복구할 흔적"
                    forge -> "강화할 수 있는 카드"
                    else -> "보유 카드"
                }
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
                        if (album.inbox.isNotEmpty()) PleosTextLink("팩 보관함 열기", packShelf)
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
                        in album.pending -> "강화 결과 확인"
                        album.featuredId -> "대표 카드"
                        highlighted -> "방금 확인한 카드"
                        else -> null
                    }
                    if (badge != null) Caption(badge, accent = true)
                    BodyText("${card.print.title}  +${card.level}")
                    Caption(
                        if (traces) {
                            "복구 대기 · ${card.grade}"
                        } else if (forge) {
                            "${card.grade} · 1회 ${points(CardRules.cost(card.grade, card.level))}"
                        } else {
                            "${dateOf(card.acquiredAt)} 획득 · ${card.grade}" +
                                if (card.variant) " · 변종" else ""
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun PackShelf(
    album: AlbumSnapshot,
    onBack: () -> Unit,
    open: (String) -> Unit,
    chances: () -> Unit,
    demoControl: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().pleosShellBackground()) {
        val wide = maxWidth >= PleosLayout.twoPaneMinWidth
        val gap = PleosLayout.itemGap
        LazyVerticalGrid(
            GridCells.Fixed(if (wide) 3 else 1),
            modifier = Modifier.testTag("pack-shelf-grid"),
            contentPadding = PaddingValues(horizontal = PleosLayout.horizontalPadding(wide), vertical = gap),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val awaiting = album.inbox.count { album.byPack[it.id] != null }
                val note = "미개봉 ${album.inbox.size - awaiting}개" + if (awaiting == 0) "" else " · 앞면 대기 ${awaiting}개"
                AlbumHeading("팩 보관함", wide, note, onBack)
            }
            item(span = { GridItemSpan(maxLineSpan) }) { demoControl() }
            if (album.inbox.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AlbumPanel {
                        SectionTitle("보관한 팩을 모두 열었어요")
                        PleosTextLink("컬렉션 보기", onBack)
                    }
                }
            }
            if (wide && album.inbox.size == 1) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val pack = album.inbox.single()
                    val drawn = pack.id in album.byPack
                    AlbumPanel {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PackStage(pack, drawn, Modifier.weight(1f).height(340.dp * PleosTextStyles.scale))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                                Caption(if (drawn) "개봉 완료 · 앞면 미확인" else "내게 도착한 한 팩", accent = true)
                                SectionTitle(pack.title)
                                Caption("${dateOf(pack.receivedAt)} · ${pack.source}")
                                BodyText(
                                    if (drawn) {
                                        "봉투는 이미 열었어요. 카드를 뒤집어 앞면을 확인하세요."
                                    } else {
                                        "카드 한 장이 들어 있어요. 개봉한 뒤에도 앞면은 나중에 확인할 수 있어요."
                                    },
                                )
                                PackAction(pack, drawn, open)
                            }
                        }
                    }
                }
            } else {
                items(album.inbox, key = { it.id }) { pack ->
                    val drawn = pack.id in album.byPack
                    AlbumPanel {
                        if (wide) {
                            PackStage(pack, drawn, Modifier.fillMaxWidth().height(210.dp * PleosTextStyles.scale))
                            SectionTitle(pack.title)
                            Caption(if (drawn) "개봉 완료 · 앞면 미확인" else pack.source)
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(gap),
                            ) {
                                ShelfArtwork(
                                    pack,
                                    drawn,
                                    Modifier.width(63.dp * PleosTextStyles.scale).aspectRatio(2f / 3f),
                                )
                                Column(Modifier.weight(1f)) {
                                    SectionTitle(pack.title)
                                    Caption(if (drawn) "개봉 완료 · 앞면 미확인" else pack.source)
                                }
                            }
                        }
                        PackAction(pack, drawn, open)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { PleosTextLink("카드팩 확률 보기", chances) }
        }
    }
}

@Composable
private fun PackStage(pack: ReceivedPack, drawn: Boolean, modifier: Modifier) {
    VaultStage(modifier) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ShelfArtwork(pack, drawn, Modifier.fillMaxHeight(.90f).aspectRatio(2f / 3f))
        }
    }
}

@Composable
private fun ShelfArtwork(pack: ReceivedPack, drawn: Boolean, modifier: Modifier) {
    if (drawn) {
        Image(
            painterResource(DesignR.drawable.milestone_common_back),
            "앞면 미확인 카드",
            modifier,
            contentScale = ContentScale.Fit,
        )
    } else {
        PackArtwork(pack, modifier)
    }
}

@Composable
private fun PackAction(pack: ReceivedPack, drawn: Boolean, open: (String) -> Unit) {
    PleosButton(
        if (drawn) "앞면 확인" else "팩 열기",
        { open(pack.id) },
        Modifier.fillMaxWidth().testTag("album-pack-" + pack.id),
        showArrow = true,
    )
}
