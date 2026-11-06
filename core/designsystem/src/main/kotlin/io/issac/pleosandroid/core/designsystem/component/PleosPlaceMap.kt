package io.issac.pleosandroid.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.R
import io.issac.pleosandroid.core.designsystem.modifier.customClickable
import io.issac.pleosandroid.core.designsystem.theme.PleosShape
import io.issac.pleosandroid.core.designsystem.theme.PleosSpacing
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme

/** The caller supplies map content and positions markers. All three controls are hidden for a read-only map. */
@Composable
fun PleosPlaceMap(
    modifier: Modifier = Modifier,
    caption: String? = null,
    onZoomIn: (() -> Unit)? = null,
    onZoomOut: (() -> Unit)? = null,
    onLocate: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val scale = PleosTextStyles.scale
    val colors = PleosTheme.colors
    val shape = RoundedCornerShape(PleosSpacing.space16)
    val north = stringResource(R.string.pleos_map_north)
    Box(modifier.clip(shape).background(colors.surface).border(1.dp, colors.borderControl, shape)) {
        content()
        Box(
            Modifier.align(Alignment.TopEnd).padding(PleosSpacing.space16 * scale).size(PleosSpacing.space40 * scale)
                .background(colors.chipDefault, CircleShape).border(1.dp, colors.borderControl, CircleShape)
                .clearAndSetSemantics { contentDescription = north },
            contentAlignment = Alignment.Center,
        ) {
            Text("N", style = PleosTextStyles.meta, color = colors.textPrimary)
        }
        if (caption != null) {
            Text(
                caption,
                Modifier.align(Alignment.BottomStart).padding(PleosSpacing.space16)
                    .background(colors.chipDefault, PleosShape.chip)
                    .padding(horizontal = PleosSpacing.space16, vertical = PleosSpacing.space4),
                style = PleosTextStyles.meta,
                color = colors.textSecondary,
            )
        }
        if (onZoomIn != null || onZoomOut != null || onLocate != null) {
            Column(
                Modifier.align(Alignment.BottomEnd).padding(PleosSpacing.space16 * scale),
                verticalArrangement = Arrangement.spacedBy(PleosSpacing.space8 * scale),
            ) {
                MapControl(PleosSymbol.Plus, stringResource(R.string.pleos_map_zoom_in), onZoomIn)
                MapControl(PleosSymbol.Minus, stringResource(R.string.pleos_map_zoom_out), onZoomOut)
                MapControl(PleosSymbol.Locate, stringResource(R.string.pleos_map_locate), onLocate)
            }
        }
    }
}

@Composable
private fun MapControl(symbol: PleosSymbol, label: String, onClick: (() -> Unit)?) {
    PleosIconButton(
        symbol = symbol,
        label = label,
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        outlined = true,
    )
}

@Composable
fun PleosMapPin(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
) {
    val colors = PleosTheme.colors
    val markerTint = if (selected) colors.textOnAction else colors.textPrimary
    Column(
        modifier.customClickable(
            shape = if (showLabel) PleosShape.button else CircleShape,
            role = Role.Button,
            rippleColor = if (showLabel) colors.textPrimary else markerTint,
            onClick = onClick,
        )
            .semantics(mergeDescendants = true) {
                if (!showLabel) contentDescription = label
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MarkerCircle(selected) {
            PleosGlyph(PleosSymbol.Pin, markerTint, Modifier.size(18.dp * PleosTextStyles.scale))
        }
        if (showLabel) {
            Text(
                label,
                Modifier.background(colors.actionSecondary, PleosShape.button)
                    .padding(horizontal = PleosSpacing.space8, vertical = PleosSpacing.space4),
                style = PleosTextStyles.meta,
                color = colors.textPrimary,
            )
        }
    }
}

@Composable
fun PleosMapCluster(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.pleos_cluster_label, count)
    Box(
        modifier.customClickable(
            shape = CircleShape,
            role = Role.Button,
            rippleColor = PleosTheme.colors.actionPrimary,
            onClick = onClick,
        ).semantics { contentDescription = label },
    ) {
        MarkerCircle(selected = false) {
            Text(count.toString(), style = PleosTextStyles.meta, color = PleosTheme.colors.actionPrimary)
        }
    }
}

@Composable
private fun MarkerCircle(selected: Boolean, content: @Composable () -> Unit) {
    val scale = PleosTextStyles.scale
    val colors = PleosTheme.colors
    Box(
        Modifier.defaultMinSize(PleosSpacing.space56 * scale, PleosSpacing.space56 * scale),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier.size(
                    PleosSpacing.space56 * scale,
                ).background(colors.actionPrimary.copy(alpha = .25f), CircleShape),
            )
        }
        Box(
            Modifier.defaultMinSize(PleosSpacing.space40 * scale, PleosSpacing.space40 * scale)
                .background(if (selected) colors.actionPrimary else colors.chipDefault, CircleShape)
                .border(2.dp, if (selected) colors.actionPrimary else colors.textPrimary, CircleShape),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}
