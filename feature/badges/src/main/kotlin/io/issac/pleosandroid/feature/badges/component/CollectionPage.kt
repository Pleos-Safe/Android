package io.issac.pleosandroid.feature.badges.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.PleosBackLink
import io.issac.pleosandroid.core.designsystem.theme.PleosCorners
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.feature.badges.R

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
        propagateMinConstraints = true,
    ) {
        val insetBorder = colors.borderControl.copy(alpha = .65f)
        Box(Modifier.matchParentSize().padding(12.dp).border(.8.dp, insetBorder, InsetShape))
        content()
    }
}
