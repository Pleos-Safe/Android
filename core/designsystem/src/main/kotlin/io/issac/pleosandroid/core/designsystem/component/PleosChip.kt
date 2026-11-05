package io.issac.pleosandroid.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
    val colors = PleosTheme.colors
    val foreground = when {
        !enabled -> colors.textDisabled
        selected -> colors.actionPrimary
        else -> colors.textSecondary
    }
    Box(
        modifier = modifier
            .clip(PleosShape.chip)
            .background(if (selected) colors.chipSelected else colors.chipDefault)
            .border(.8.dp, if (selected && enabled) colors.actionPrimary else colors.borderDefault, PleosShape.chip)
            .toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = onSelectedChange)
            .sizeIn(minWidth = PleosSpacing.space64, minHeight = PleosSpacing.space40)
            .padding(horizontal = PleosSpacing.space16, vertical = PleosSpacing.space4),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = foreground, style = PleosTextStyles.meta)
    }
}
