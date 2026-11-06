package io.issac.pleosandroid.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.issac.pleosandroid.core.designsystem.R
import io.issac.pleosandroid.core.designsystem.modifier.pressOnDown
import io.issac.pleosandroid.core.designsystem.modifier.shapedRipple
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosShape
import io.issac.pleosandroid.core.designsystem.theme.PleosSpacing
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme

enum class PleosTab(@param:StringRes internal val label: Int) {
    Travel(R.string.pleos_tab_travel),
    Collection(R.string.pleos_tab_collection),
    Records(R.string.pleos_tab_records),
}

/** Pair with [PleosBottomTabs]. Only one tab strip is shown for the available width. */
@Composable
fun PleosHeader(
    selectedTab: PleosTab,
    onTabSelected: (PleosTab) -> Unit,
    onSettingsClick: () -> Unit,
    restricted: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = PleosTheme.colors
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val scale = PleosTextStyles.scale
        val full = maxWidth >= FullLayoutMinWidth * scale
        val margin = PleosLayout.horizontalPadding(full)
        Box(
            Modifier.fillMaxWidth()
                .padding(horizontal = margin)
                .height((if (full) 80.dp else 72.dp) * scale),
        ) {
            Row(
                Modifier.fillMaxWidth().align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PleosSpacing.space12),
            ) {
                PleosGlyph(PleosSymbol.Mark, colors.textPrimary, Modifier.size(30.dp * scale))
                Text(
                    stringResource(R.string.pleos_brand),
                    Modifier.weight(1f),
                    style = PleosTextStyles.body.copy(fontWeight = FontWeight.Medium, letterSpacing = .8.sp),
                    color = colors.textPrimary,
                    maxLines = 1,
                )
                if (!restricted) {
                    PleosIconButton(
                        PleosSymbol.Settings,
                        stringResource(R.string.pleos_settings),
                        onSettingsClick,
                        Modifier.padding(end = if (full) PleosSpacing.space56 else PleosSpacing.space16),
                    )
                }
                Text(
                    stringResource(if (restricted) R.string.pleos_restricted else R.string.pleos_parked),
                    style = PleosTextStyles.meta,
                    color = colors.textSecondary,
                )
            }
            if (full && !restricted) {
                TabStrip(
                    selectedTab,
                    onTabSelected,
                    Modifier.align(Alignment.Center).width(336.dp * scale),
                    bottom = false,
                )
            }
            HorizontalDivider(Modifier.align(Alignment.BottomCenter), color = colors.borderDefault)
        }
    }
}

@Composable
fun PleosBottomTabs(
    selectedTab: PleosTab,
    onTabSelected: (PleosTab) -> Unit,
    restricted: Boolean,
    modifier: Modifier = Modifier,
) {
    if (restricted) return
    val colors = PleosTheme.colors
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < FullLayoutMinWidth * PleosTextStyles.scale) {
            Box(Modifier.fillMaxWidth().background(colors.surface)) {
                HorizontalDivider(color = colors.borderDefault)
                TabStrip(selectedTab, onTabSelected, Modifier.fillMaxWidth(), bottom = true)
            }
        }
    }
}

@Composable
private fun TabStrip(selectedTab: PleosTab, onSelect: (PleosTab) -> Unit, modifier: Modifier, bottom: Boolean) {
    val colors = PleosTheme.colors
    val scale = PleosTextStyles.scale
    Row(modifier.height(PleosSpacing.space56 * scale).selectableGroup()) {
        PleosTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            val interactions = remember { MutableInteractionSource() }
            val foreground = if (selected) colors.actionPrimary else colors.textSecondary
            val select = { onSelect(tab) }
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .selectable(
                        selected = selected,
                        interactionSource = interactions,
                        indication = null,
                        role = Role.Tab,
                        onClick = select,
                    )
                    .pressOnDown(interactions, onClick = select)
                    .shapedRipple(interactions, PleosShape.button, foreground),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(tab.label),
                    style = PleosTextStyles.body,
                    color = foreground,
                )
                if (selected) {
                    Spacer(
                        Modifier.align(if (bottom) Alignment.TopCenter else Alignment.BottomCenter)
                            .size((if (tab == PleosTab.Collection) 50.dp else 34.dp) * scale, 2.dp * scale)
                            .background(colors.actionPrimary),
                    )
                }
            }
        }
    }
}
