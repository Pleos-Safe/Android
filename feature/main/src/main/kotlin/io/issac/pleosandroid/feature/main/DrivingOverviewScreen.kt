package io.issac.pleosandroid.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.pleosShellBackground
import io.issac.pleosandroid.core.designsystem.theme.PleosShape
import io.issac.pleosandroid.core.designsystem.theme.PleosSpacing
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme

@Composable
internal fun DrivingOverviewScreen(
    isMoving: Boolean,
    modifier: Modifier = Modifier,
    map: @Composable (Modifier) -> Unit,
    recommendation: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().pleosShellBackground()) {
        val wide = maxWidth >= WideOverviewMinWidth
        Column(
            modifier = Modifier.fillMaxSize().padding(
                horizontal = if (wide) PleosSpacing.contentHorizontalFull else PleosSpacing.space24,
                vertical = PleosSpacing.space16,
            ),
            verticalArrangement = Arrangement.spacedBy(PleosSpacing.space16),
        ) {
            DrivingOverviewHeading(isMoving, wide)
            if (wide) {
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(PleosSpacing.space24),
                ) {
                    DrivingOverviewMap(map, Modifier.weight(1f).fillMaxSize())
                    Column(
                        Modifier.width(OverviewSidePanelWidth),
                        verticalArrangement = Arrangement.spacedBy(PleosSpacing.space12),
                    ) {
                        DrivingOverviewRecommendation(recommendation)
                    }
                }
            } else {
                // The map uses the space remaining after the recommendation and its action are measured.
                DrivingOverviewMap(map, Modifier.weight(1f).fillMaxWidth())
                DrivingOverviewRecommendation(recommendation)
            }
        }
    }
}

@Composable
private fun DrivingOverviewHeading(isMoving: Boolean, wide: Boolean) {
    val colors = PleosTheme.colors
    val statusColor = if (isMoving) colors.actionPrimary else colors.statusPending
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PleosSpacing.space16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                if (isMoving) R.string.journey_restriction_moving_title else R.string.journey_restriction_stopped_title,
            ),
            modifier = Modifier.weight(1f).semantics { heading() },
            color = colors.textPrimary,
            style = if (wide) PleosTextStyles.titleFull else PleosTextStyles.titleSplit,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier
                .background(colors.chipDefault, PleosShape.chip)
                .border(1.dp, colors.borderDefault, PleosShape.chip)
                .padding(horizontal = PleosSpacing.space12, vertical = PleosSpacing.space8),
            horizontalArrangement = Arrangement.spacedBy(PleosSpacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(PleosSpacing.space8).background(statusColor, CircleShape))
            Text(
                text = stringResource(
                    if (isMoving) R.string.driving_overview_moving else R.string.driving_overview_stopped,
                ),
                color = statusColor,
                style = PleosTextStyles.meta,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DrivingOverviewMap(map: @Composable (Modifier) -> Unit, modifier: Modifier = Modifier) {
    val colors = PleosTheme.colors
    Box(
        modifier.clip(PleosShape.stage)
            .background(colors.surface)
            .border(1.dp, colors.borderControl, PleosShape.stage),
    ) {
        map(Modifier.fillMaxSize())
    }
}

@Composable
private fun DrivingOverviewRecommendation(recommendation: @Composable () -> Unit) {
    val colors = PleosTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(colors.surface, PleosShape.inset)
            .border(1.dp, colors.borderDefault, PleosShape.inset)
            .padding(horizontal = PleosSpacing.space16, vertical = PleosSpacing.space12),
        verticalArrangement = Arrangement.spacedBy(PleosSpacing.space4),
    ) {
        Text(
            text = stringResource(R.string.driving_overview_recommendation),
            color = colors.textPrimary,
            style = PleosTextStyles.body,
            modifier = Modifier.semantics { heading() },
        )
        HorizontalDivider(color = colors.borderDefault)
        recommendation()
    }
}

private val WideOverviewMinWidth = 1000.dp
private val OverviewSidePanelWidth = 352.dp
