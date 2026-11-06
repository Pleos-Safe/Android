package io.issac.pleosandroid.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.modifier.pressOnDown
import io.issac.pleosandroid.core.designsystem.modifier.shapedRipple
import io.issac.pleosandroid.core.designsystem.theme.PleosShape
import io.issac.pleosandroid.core.designsystem.theme.PleosSpacing
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme

@Composable
fun PleosChip(
    label: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scale = PleosTextStyles.scale
    val colors = PleosTheme.colors
    val interactions = remember { MutableInteractionSource() }
    val foreground = when {
        !enabled -> colors.textDisabled
        selected -> colors.actionPrimary
        else -> colors.textSecondary
    }
    Box(
        modifier = modifier
            .toggleable(
                value = selected,
                interactionSource = interactions,
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onSelectedChange,
            )
            .pressOnDown(interactions, enabled) { onSelectedChange(!selected) }
            .shapedRipple(interactions, PleosShape.chip, foreground, enabled)
            .background(if (selected) colors.chipSelected else colors.chipDefault)
            .border(.8.dp, if (selected && enabled) colors.actionPrimary else colors.borderDefault, PleosShape.chip)
            .sizeIn(minWidth = PleosSpacing.space64 * scale, minHeight = PleosSpacing.space40 * scale)
            .padding(horizontal = PleosSpacing.space16 * scale, vertical = PleosSpacing.space4 * scale),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = foreground, style = PleosTextStyles.meta)
    }
}
