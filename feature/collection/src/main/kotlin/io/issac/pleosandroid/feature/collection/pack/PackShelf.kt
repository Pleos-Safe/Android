package io.issac.pleosandroid.feature.collection.pack

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.cardui.component.AlbumHeading
import io.issac.pleosandroid.core.cardui.component.AlbumPanel
import io.issac.pleosandroid.core.cardui.component.BodyText
import io.issac.pleosandroid.core.cardui.component.Caption
import io.issac.pleosandroid.core.cardui.component.SectionTitle
import io.issac.pleosandroid.core.cardui.component.VaultStage
import io.issac.pleosandroid.core.cardui.component.dateOf
import io.issac.pleosandroid.core.designsystem.R as DesignR
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.component.pleosShellBackground
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.domain.cards.AlbumSnapshot
import io.issac.pleosandroid.core.domain.cards.ReceivedPack

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
                                Caption(if (drawn) "개봉 완료 · 앞면 미확인" else "미개봉", accent = true)
                                SectionTitle(pack.title)
                                Caption("${dateOf(pack.receivedAt)} · ${pack.source}")
                                BodyText(
                                    if (drawn) {
                                        "카드를 뒤집으면 컬렉션에 담겨요."
                                    } else {
                                        "여행 카드 한 장이 들어 있어요."
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
