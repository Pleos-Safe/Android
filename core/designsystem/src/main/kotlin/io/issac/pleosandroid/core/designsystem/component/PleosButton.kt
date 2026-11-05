package io.issac.pleosandroid.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val colors = PleosTheme.colors
    val secondary = style == PleosButtonStyle.Secondary
    val container = when (style) {
        PleosButtonStyle.Primary -> colors.actionPrimary
        PleosButtonStyle.Secondary -> colors.actionSecondary
        PleosButtonStyle.Danger -> colors.actionDanger
    }
    val content = if (secondary) colors.textPrimary else colors.textOnAction

    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (secondary) PleosSpacing.space48 else PleosSpacing.space56)
            .width(IntrinsicSize.Max),
        enabled = enabled,
        shape = PleosShape.button,
        border = if (secondary) BorderStroke(1.dp, colors.borderControl) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = colors.actionDisabled,
            disabledContentColor = colors.textDisabled,
        ),
        contentPadding = PaddingValues(horizontal = PleosSpacing.space24, vertical = PleosSpacing.space8),
    ) {
        Text(
            text = text,
            modifier = if (showArrow) Modifier.weight(1f) else Modifier,
            style = PleosTextStyles.body.copy(fontWeight = FontWeight.Medium),
        )
        if (enabled && showArrow) {
            Spacer(Modifier.width(PleosSpacing.space16))
            PleosGlyph(PleosSymbol.Arrow, content, Modifier.size(20.dp))
        }
    }
}

@Composable
fun PleosTextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = PleosTheme.colors
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = PleosSpacing.space48),
        enabled = enabled,
        shape = PleosShape.button,
        colors = ButtonDefaults.textButtonColors(
            contentColor = colors.actionPrimary,
            disabledContentColor = colors.textDisabled,
        ),
        contentPadding = PaddingValues(horizontal = PleosSpacing.space4),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PleosSpacing.space12),
        ) {
            Text(text, style = PleosTextStyles.meta)
            if (enabled) PleosGlyph(PleosSymbol.Arrow, colors.actionPrimary, Modifier.size(PleosSpacing.space16))
        }
    }
}
