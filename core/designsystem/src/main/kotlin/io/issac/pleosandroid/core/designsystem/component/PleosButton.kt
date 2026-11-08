package io.issac.pleosandroid.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.modifier.customClickable
import io.issac.pleosandroid.core.designsystem.theme.PleosShape
import io.issac.pleosandroid.core.designsystem.theme.PleosSpacing
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme

enum class PleosButtonStyle { Primary, Secondary, Danger }

@Composable
fun PleosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: PleosButtonStyle = PleosButtonStyle.Primary,
    showArrow: Boolean = false,
) {
    val scale = PleosTextStyles.scale
    val colors = PleosTheme.colors
    val secondary = style == PleosButtonStyle.Secondary
    val container = when (style) {
        PleosButtonStyle.Primary -> colors.actionPrimary
        PleosButtonStyle.Secondary -> colors.actionSecondary
        PleosButtonStyle.Danger -> colors.actionDanger
    }
    val content = when {
        !enabled -> colors.textDisabled
        secondary -> colors.textPrimary
        else -> colors.textOnAction
    }
    val border = if (secondary) Modifier.border(1.dp, colors.borderControl, PleosShape.button) else Modifier

    Row(
        modifier = modifier
            .heightIn(min = (if (secondary) PleosSpacing.space48 else PleosSpacing.space56) * scale)
            .width(IntrinsicSize.Max)
            .customClickable(enabled = enabled, role = Role.Button, rippleColor = content, onClick = onClick)
            .background(if (enabled) container else colors.actionDisabled, PleosShape.button)
            .then(border)
            .defaultMinSize(minWidth = ButtonDefaults.MinWidth)
            .padding(horizontal = PleosSpacing.space24 * scale, vertical = PleosSpacing.space8 * scale),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = if (showArrow) Modifier.weight(1f) else Modifier,
            style = PleosTextStyles.body.copy(fontWeight = FontWeight.Medium),
            color = content,
            textAlign = if (showArrow) TextAlign.Start else TextAlign.Center,
        )
        if (enabled && showArrow) {
            Spacer(Modifier.width(PleosSpacing.space16 * scale))
            PleosGlyph(PleosSymbol.Arrow, content, Modifier.size(20.dp * scale))
        }
    }
}

@Composable
fun PleosTextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val scale = PleosTextStyles.scale
    val colors = PleosTheme.colors
    val content = if (enabled) colors.actionPrimary else colors.textDisabled
    Row(
        modifier = modifier.heightIn(min = PleosSpacing.space48 * scale)
            .customClickable(enabled = enabled, role = Role.Button, rippleColor = content, onClick = onClick)
            .defaultMinSize(minWidth = ButtonDefaults.MinWidth)
            .padding(horizontal = PleosSpacing.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PleosSpacing.space8 * scale, Alignment.CenterHorizontally),
    ) {
        Text(text, Modifier.weight(1f, fill = false), style = PleosTextStyles.meta, color = content)
        if (enabled) PleosGlyph(PleosSymbol.Arrow, content, Modifier.size(PleosSpacing.space16 * scale))
    }
}
