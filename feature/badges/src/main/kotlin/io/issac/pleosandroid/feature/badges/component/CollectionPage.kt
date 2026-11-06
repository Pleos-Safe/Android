package io.issac.pleosandroid.feature.badges.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.FullLayoutMinWidth
import io.issac.pleosandroid.core.designsystem.component.MILESTONE_CARD_ASPECT_RATIO
import io.issac.pleosandroid.core.designsystem.component.MilestoneCard
import io.issac.pleosandroid.core.designsystem.component.MilestoneCardFace
import io.issac.pleosandroid.core.designsystem.component.PleosBackLink
import io.issac.pleosandroid.core.designsystem.component.pleosShellBackground
import io.issac.pleosandroid.core.designsystem.theme.PleosCorners
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosSpacing
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.feature.badges.R

internal data class CollectionWindow(val width: Dp, val height: Dp, val scale: Float = 1f) {
    val full: Boolean get() = width >= FullLayoutMinWidth * scale
    private val extraHeight: Dp get() = (height - if (full) 680.dp else 616.dp).coerceAtLeast(0.dp)
    val tall: Boolean get() = extraHeight >= 96.dp

    /** Grow the print beyond the design viewport, preserving the physical postcard proportions. */
    fun printWidth(wide: Dp, split: Dp, heightShare: Float = 1f): Dp =
        (if (full) wide else split) + extraHeight * MILESTONE_CARD_ASPECT_RATIO * heightShare
}

@Composable
internal fun CollectionPage(
    modifier: Modifier = Modifier,
    compactSpacing: Dp = PleosSpacing.space16,
    fullTopPadding: Dp = 20.dp,
    content: @Composable ColumnScope.(CollectionWindow) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().pleosShellBackground()) {
        val window = CollectionWindow(maxWidth, maxHeight, PleosTextStyles.scale)
        val full = window.full
        val margin = PleosLayout.horizontalPadding(full)
        Column(
            Modifier.fillMaxSize().verticalScroll(
                rememberScrollState(),
            ).padding(
                start = margin,
                end = margin,
                top = (if (full) fullTopPadding else 20.dp) * window.scale,
                bottom = 20.dp * window.scale,
            ),
            verticalArrangement = Arrangement.spacedBy(
                (if (full) PleosSpacing.space16 else compactSpacing) * window.scale,
            ),
        ) { content(window) }
    }
}

@Composable
internal fun CollectionTitle(text: String, full: Boolean, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.semantics { heading() },
        style = if (full) PleosTextStyles.titleFull else PleosTextStyles.titleSplit,
        color = PleosTheme.colors.textPrimary,
    )
}

@Composable
internal fun Caption(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Text(
        text,
        modifier,
        style = PleosTextStyles.meta,
        color = if (accent) PleosTheme.colors.actionPrimary else PleosTheme.colors.textSecondary,
    )
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = PleosTextStyles.section, color = PleosTheme.colors.textPrimary)
}

@Composable
internal fun CollectionBack(label: String, onBack: () -> Unit) {
    val back = stringResource(R.string.collection_back)
    PleosBackLink(label, onBack, Modifier.semantics { contentDescription = back })
}

@Composable
internal fun CollectionColumns(
    full: Boolean,
    modifier: Modifier = Modifier,
    leftWeight: Float = 1f,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    if (full) {
        Row(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PleosLayout.columnGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(leftWeight)) { first() }
            Box(Modifier.weight(1f)) { second() }
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PleosLayout.sectionGap)) {
            first()
            second()
        }
    }
}

private val VaultShape =
    CutCornerShape(topStart = PleosCorners.stageCut, topEnd = PleosCorners.stageCut, bottomEnd = PleosCorners.stageCut)
private val InsetShape =
    CutCornerShape(topStart = PleosCorners.insetCut, topEnd = PleosCorners.insetCut, bottomEnd = PleosCorners.insetCut)

@Composable
internal fun VaultStage(
    modifier: Modifier = Modifier,
    highlighted: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = PleosTheme.colors
    val foil = if (highlighted) {
        listOf(
            colors.actionPrimary,
            colors.accentLilac,
            colors.statusAttention,
            colors.statusPending,
            colors.actionPrimary,
        )
    } else {
        listOf(colors.borderControl, colors.borderDefault)
    }
    Box(
        modifier.clip(VaultShape).background(Brush.linearGradient(listOf(Color(0xFF1D2B4B), Color(0xFF0B1226))))
            .border(1.dp, Brush.linearGradient(foil), VaultShape)
            .drawBehind {
                val glint = Path().apply {
                    moveTo(size.width * .62f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height * .53f)
                    close()
                }
                drawPath(glint, colors.accentLilac.copy(alpha = .045f))
            },
    ) {
        val insetBorder = colors.borderControl.copy(alpha = .65f)
        Box(Modifier.matchParentSize().padding(12.dp).border(.8.dp, insetBorder, InsetShape))
        content()
    }
}

@Composable
internal fun DisplayedArt(face: MilestoneCardFace, width: Dp, modifier: Modifier = Modifier) {
    CardOnShelf(width, modifier) { MilestoneCard(face, it) }
}

@Composable
private fun CardOnShelf(width: Dp, modifier: Modifier = Modifier, print: @Composable (Modifier) -> Unit) {
    val colors = PleosTheme.colors
    val sideInset = 6.dp
    val shadowGap = 3.dp
    val shadowHeight = 22.dp
    BoxWithConstraints(modifier.widthIn(max = width + sideInset * 2), contentAlignment = Alignment.Center) {
        val heightLimit = if (constraints.hasBoundedHeight) {
            (maxHeight - shadowGap - shadowHeight) * MILESTONE_CARD_ASPECT_RATIO
        } else {
            Dp.Infinity
        }
        val printWidth = minOf(width, maxWidth - sideInset * 2, heightLimit).coerceAtLeast(0.dp)
        Column(Modifier.width(printWidth + sideInset * 2), horizontalAlignment = Alignment.CenterHorizontally) {
            print(Modifier.padding(horizontal = sideInset).width(printWidth))
            Canvas(Modifier.fillMaxWidth().padding(top = shadowGap).height(shadowHeight)) {
                drawOval(Color(0xAD030710), size = Size(size.width, size.height * .78f))
                drawLine(colors.borderControl, Offset(0f, size.height), Offset(size.width, size.height), .7.dp.toPx())
            }
        }
    }
}

@Composable
internal fun ArtStage(face: MilestoneCardFace, width: Dp, caption: String, modifier: Modifier = Modifier) {
    ArtStage(width, caption, modifier) { MilestoneCard(face, it) }
}

/** In split view the actions stack underneath, so measure them before assigning the print its height. */
@Composable
internal fun ColumnScope.CardExhibit(
    window: CollectionWindow,
    designWidth: Dp,
    caption: String,
    stage: Modifier = Modifier,
    print: @Composable (Modifier) -> Unit,
    info: @Composable () -> Unit,
) {
    if (!window.tall) {
        CollectionColumns(window.full, first = { ArtStage(designWidth, caption, stage, print = print) }, second = info)
    } else if (window.full) {
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtStage(designWidth, caption, Modifier.weight(1f).fillMaxHeight().then(stage), fit = true, print = print)
            Box(Modifier.weight(1f)) { info() }
        }
    } else {
        ArtStage(designWidth, caption, Modifier.fillMaxWidth().weight(1f).then(stage), fit = true, print = print)
        info()
    }
}

@Composable
internal fun ArtStage(
    width: Dp,
    caption: String,
    modifier: Modifier = Modifier,
    fit: Boolean = false,
    print: @Composable (Modifier) -> Unit,
) {
    VaultStage(modifier.fillMaxWidth()) {
        Column(
            (if (fit) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (fit) {
                CardOnShelf(Dp.Infinity, Modifier.fillMaxWidth().weight(1f), print = print)
            } else {
                CardOnShelf(width, print = print)
            }
            if (caption.isNotBlank()) Caption(caption, Modifier.align(Alignment.Start), accent = true)
        }
    }
}
