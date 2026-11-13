package io.issac.pleosandroid.feature.membership

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme

@Composable
internal fun PlusPass(modifier: Modifier = Modifier) {
    val colors = PleosTheme.colors
    val type = PleosTextStyles
    val cut = CutCornerShape(topStart = 18.dp * type.scale, bottomEnd = 18.dp * type.scale)
    val foil = Brush.linearGradient(listOf(colors.actionPrimary, colors.accentSecondary, colors.accentHighlight))
    Column(
        modifier.fillMaxWidth().clip(cut)
            .background(Brush.linearGradient(listOf(colors.shellStart, colors.actionSecondary, colors.chipSelected)))
            .drawWithCache {
                // An embossed plus is part of the pass artwork and carries no additional reading order.
                val unit = minOf(size.height * .105f, size.width * .045f)
                val cx = size.width - 3 * unit - 18.dp.toPx()
                val cy = size.height * .52f
                val mark = Path().apply {
                    moveTo(cx - unit, cy - 3 * unit)
                    lineTo(cx + unit, cy - 3 * unit)
                    lineTo(cx + unit, cy - unit)
                    lineTo(cx + 3 * unit, cy - unit)
                    lineTo(cx + 3 * unit, cy + unit)
                    lineTo(cx + unit, cy + unit)
                    lineTo(cx + unit, cy + 3 * unit)
                    lineTo(cx - unit, cy + 3 * unit)
                    lineTo(cx - unit, cy + unit)
                    lineTo(cx - 3 * unit, cy + unit)
                    lineTo(cx - 3 * unit, cy - unit)
                    lineTo(cx - unit, cy - unit)
                    close()
                }
                val sheen = Brush.linearGradient(
                    listOf(colors.actionPrimary, colors.accentSecondary, colors.accentHighlight),
                    Offset(cx - 3 * unit, cy - 3 * unit),
                    Offset(cx + 3 * unit, cy + 3 * unit),
                )
                onDrawBehind {
                    drawPath(mark, sheen, alpha = .11f)
                    drawPath(mark, sheen, alpha = .36f, style = Stroke(width = 1.dp.toPx()))
                }
            }
            .border(1.dp * type.scale, foil, cut)
            .padding(PleosLayout.panelPadding)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(
            PleosLayout.sectionGap,
            Alignment.CenterVertically,
        ),
    ) {
        Text("차곡차곡 PLUS", style = type.section, color = colors.actionPrimary)
        Column {
            Text(
                "1.5배",
                style = type.titleFull.copy(
                    fontSize = type.titleFull.fontSize * 1.75f,
                    lineHeight =
                        type.titleFull.lineHeight * 1.65f,
                ),
                color = colors.textPrimary,
            )
            Text("주행 포인트 적립", style = type.body, color = colors.textSecondary)
        }
    }
}
