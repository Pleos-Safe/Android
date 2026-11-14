package io.issac.pleosandroid.feature.collection.gallery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.cardui.component.AlbumHeading
import io.issac.pleosandroid.core.cardui.component.Caption
import io.issac.pleosandroid.core.cardui.component.VaultStage
import io.issac.pleosandroid.core.cardui.preview.previewForgeCard
import io.issac.pleosandroid.core.cardui.preview.previewPackCard
import io.issac.pleosandroid.core.cardui.print.CardPrintView
import io.issac.pleosandroid.core.designsystem.R as DesignR
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosButtonStyle
import io.issac.pleosandroid.core.designsystem.component.PleosChip
import io.issac.pleosandroid.core.designsystem.component.PleosInfoPanel
import io.issac.pleosandroid.core.designsystem.component.pleosShellBackground
import io.issac.pleosandroid.core.designsystem.modifier.customClickable
import io.issac.pleosandroid.core.designsystem.theme.PleosCorners
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.navigation.CardMotionRoute
import io.issac.pleosandroid.core.navigation.ForgeMotionRoute
import io.issac.pleosandroid.core.navigation.MotionGalleryRoute
import io.issac.pleosandroid.core.navigation.PackMotionRoute
import io.issac.pleosandroid.feature.collection.pack.PackArtwork

@Composable
internal fun MotionGallery(
    route: MotionGalleryRoute,
    onPlay: (CardMotionRoute) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var group by rememberSaveable(route) {
        mutableStateOf(if (route.startOnForge) MotionGroup.Outcomes else MotionGroup.Packs)
    }
    var level by rememberSaveable { mutableIntStateOf(14) }
    val choices = remember(group, level) { motionChoices(group, level) }
    BoxWithConstraints(modifier.fillMaxSize().pleosShellBackground()) {
        val wide = maxWidth >= PleosLayout.twoPaneMinWidth
        val gap = PleosLayout.itemGap
        val scale = PleosTextStyles.scale
        val posterHeight = when {
            !wide -> null
            group == MotionGroup.Packs -> (maxHeight * .40f).coerceIn(132.dp * scale, 290.dp * scale)
            else -> (maxHeight * .17f).coerceIn(84.dp * scale, 140.dp * scale)
        }
        val minWidth = if (wide && group == MotionGroup.Outcomes) 300.dp else 245.dp
        LazyVerticalGrid(
            GridCells.Adaptive(minWidth * scale),
            modifier = Modifier.fillMaxSize().testTag("motion-gallery"),
            contentPadding = PaddingValues(horizontal = PleosLayout.horizontalPadding(wide), vertical = gap),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AlbumHeading("카드 연출 모아보기", wide, back = onBack)
                    Caption("샘플 카드로 재생해요. 내 카드와 강화 포인트는 소모되지 않아요.")
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (tab in MotionGroup.entries) PleosChip(tab.title, group == tab, { group = tab })
                }
            }
            if (group == MotionGroup.Outcomes) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("시작 강화  +$level", style = PleosTextStyles.body, color = PleosTheme.colors.textPrimary)
                        PleosButton("−1", {
                            level = (level - 1).coerceAtLeast(0)
                        }, enabled = level > 0, style = PleosButtonStyle.Secondary)
                        PleosButton("+1", {
                            level = (level + 1).coerceAtMost(Int.MAX_VALUE - 2)
                        }, enabled = level < Int.MAX_VALUE - 2, style = PleosButtonStyle.Secondary)
                    }
                }
            }
            items(choices, key = { it.title }) { choice ->
                MotionTile(choice, posterHeight, onPlay)
            }
        }
    }
}

@Composable
private fun MotionTile(choice: MotionChoice, posterHeight: Dp?, onPlay: (CardMotionRoute) -> Unit) {
    val shape = CutCornerShape(topStart = PleosCorners.insetCut, bottomEnd = PleosCorners.insetCut)
    PleosInfoPanel(
        Modifier.fillMaxWidth().alpha(if (choice.enabled) 1f else .50f)
            .customClickable(shape, enabled = choice.enabled, onClickLabel = "연출 재생", role = Role.Button) {
                onPlay(choice.scene)
            },
    ) {
        if (posterHeight != null) {
            VaultStage(Modifier.fillMaxWidth().height(posterHeight).clearAndSetSemantics {}, highlighted = false) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Box(Modifier.fillMaxHeight(.84f).aspectRatio(2f / 3f), Alignment.Center) {
                        MotionThumbnail(choice.scene)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (posterHeight == null) {
                Box(
                    Modifier.width(56.dp * PleosTextStyles.scale).aspectRatio(2f / 3f).clearAndSetSemantics {},
                    Alignment.Center,
                ) { MotionThumbnail(choice.scene) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(choice.title, style = PleosTextStyles.body, color = PleosTheme.colors.textPrimary)
                Caption(choice.detail)
            }
            if (choice.enabled) {
                val tint = PleosTheme.colors.actionPrimary
                Canvas(Modifier.size(14.dp * PleosTextStyles.scale)) {
                    val play = Path().apply {
                        moveTo(size.width * .2f, 0f)
                        lineTo(size.width, size.height * .5f)
                        lineTo(size.width * .2f, size.height)
                        close()
                    }
                    drawPath(play, tint)
                }
            }
        }
    }
}

@Composable
private fun MotionThumbnail(scene: CardMotionRoute) {
    when (scene) {
        is PackMotionRoute -> if (scene.flipOnly) {
            Image(painterResource(DesignR.drawable.chagok_common_back), null, Modifier.fillMaxSize())
        } else {
            PackArtwork(remember(scene) { previewPackCard(scene.region, scene.grade).pack }, Modifier.fillMaxSize())
        }

        is ForgeMotionRoute -> CardPrintView(
            remember(scene) {
                previewForgeCard(scene.after, scene.outcome)
            },
            Modifier.fillMaxSize(),
        )
    }
}
