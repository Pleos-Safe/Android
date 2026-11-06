package io.issac.pleosandroid.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.modifier.customClickable
import io.issac.pleosandroid.core.designsystem.theme.PleosShape
import io.issac.pleosandroid.core.designsystem.theme.PleosSpacing
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme

internal enum class PleosSymbol(val outline: String) {
    Arrow("M2 12H22M15 5L22 12L15 19"),
    Mark("M1 19V1H5L12 13L19 1H23V19"),
    Search("M17 17L23 23M10 2A8 8 0 1 0 10 18A8 8 0 1 0 10 2Z"),
    Close("M5 5L19 19M19 5L5 19"),
    Settings(
        "M9 2H15L16 5L19 6L22 10V14L19 18L16 19L15 22H9L8 19L5 18L2 14V10L5 6L8 5Z " +
            "M12 7A5 5 0 1 0 12 17A5 5 0 1 0 12 7Z",
    ),
    Pin("M12 22S4 14 4 9A8 8 0 1 1 20 9C20 14 12 22 12 22ZM12 6V10"),
    Locate("M12 1V5M12 19V23M1 12H5M19 12H23M12 5A7 7 0 1 0 12 19A7 7 0 1 0 12 5Z"),
    Plus("M5 12H19M12 5V19"),
    Minus("M5 12H19"),
    Pause("M8 4V20M16 4V20"),
    Lock("M5 10H19V22H5ZM8 10V6A4 4 0 0 1 16 6V10M12 15V18"),
}

@Composable
internal fun PleosGlyph(symbol: PleosSymbol, tint: Color, modifier: Modifier = Modifier) {
    val path = remember(symbol) { PathParser().parsePathString(symbol.outline).toPath() }
    val accent = PleosTheme.colors.actionPrimary
    Canvas(modifier.size(PleosSpacing.space24)) {
        withTransform({ scale(size.width / 24f, size.height / 24f, Offset.Zero) }) {
            drawPath(path, tint, style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (symbol == PleosSymbol.Mark) drawLine(accent, Offset(1f, 23f), Offset(23f, 23f), 1.4f)
        }
    }
}

@Composable
fun PleosRestrictionGlyph(isMoving: Boolean, modifier: Modifier = Modifier) {
    PleosGlyph(
        symbol = if (isMoving) PleosSymbol.Pause else PleosSymbol.Lock,
        tint = PleosTheme.colors.textSecondary,
        modifier = modifier.size(PleosSpacing.space48),
    )
}

@Composable
internal fun PleosIconButton(
    symbol: PleosSymbol,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
) {
    val colors = PleosTheme.colors
    val tint = if (enabled) colors.textPrimary else colors.textDisabled
    val shape = if (outlined) PleosShape.button else CircleShape
    val outline = if (outlined) {
        Modifier.background(colors.chipDefault, shape).border(1.dp, colors.borderControl, shape)
    } else {
        Modifier
    }
    Box(
        modifier = modifier.size(PleosSpacing.space48)
            .semantics { contentDescription = label }
            .customClickable(
                shape = shape,
                enabled = enabled,
                role = Role.Button,
                rippleColor = tint,
                onClick = onClick,
            )
            .then(outline),
        contentAlignment = Alignment.Center,
    ) {
        PleosGlyph(symbol, tint)
    }
}
