package io.issac.pleosandroid.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.modifier.customClickable
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosSpacing
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme

val FullLayoutMinWidth = 1000.dp

@Composable
fun Modifier.pleosShellBackground(): Modifier {
    val colors = PleosTheme.colors
    val brush = remember(colors.shellStart, colors.shellEnd) {
        Brush.linearGradient(listOf(colors.shellStart, colors.shellEnd))
    }
    return background(brush)
}

/** Shared scrollable screen shell. Scaffold insets are supplied by the caller's modifier. */
@Composable
fun PleosScreen(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    // Each page needs its own opaque surface when another page slides over it.
    BoxWithConstraints(modifier = modifier.fillMaxSize().pleosShellBackground()) {
        val isFull = maxWidth >= FullLayoutMinWidth * PleosTextStyles.scale
        val horizontalPadding = PleosLayout.horizontalPadding(isFull)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = PleosLayout.sectionGap),
            verticalArrangement = Arrangement.spacedBy(PleosLayout.sectionGap),
        ) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = if (isFull) PleosTextStyles.titleFull else PleosTextStyles.titleSplit,
                color = PleosTheme.colors.textPrimary,
            )
            content()
        }
    }
}

@Composable
fun PleosBackLink(label: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val scale = PleosTextStyles.scale
    val colors = PleosTheme.colors
    Row(
        modifier.customClickable(role = Role.Button, rippleColor = colors.actionPrimary, onClick = onBack)
            .heightIn(min = PleosSpacing.space48 * scale)
            .padding(horizontal = PleosSpacing.space8 * scale),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PleosLayout.itemGap),
    ) {
        PleosGlyph(PleosSymbol.Back, colors.actionPrimary, Modifier.size(20.dp * scale))
        Text(label, style = PleosTextStyles.meta, color = colors.textSecondary)
    }
}
