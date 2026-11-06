package io.issac.pleosandroid.feature.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.R as DesignR
import io.issac.pleosandroid.core.designsystem.component.MilestoneCard
import io.issac.pleosandroid.core.designsystem.component.MilestoneCardFace
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosInfoPanel
import io.issac.pleosandroid.core.designsystem.component.PleosScreen
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.theme.PleosShape
import io.issac.pleosandroid.core.designsystem.theme.PleosSpacing
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme

@Composable
internal fun ParkedTravelHome(
    onStartJourney: () -> Unit,
    onOpenBadges: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenMap: (() -> Unit)? = null,
) {
    val colors = PleosTheme.colors
    PleosScreen(title = stringResource(R.string.journey_home_title), modifier = modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.journey_home_recommendation),
                style = PleosTextStyles.meta,
                color = colors.actionPrimary,
            )
            Text(
                text = stringResource(R.string.journey_before_driving),
                style = PleosTextStyles.meta,
                color = colors.textSecondary,
            )
        }
        ParkedJourneyColumns(
            primaryWeight = 1.8f,
            primary = { wide ->
                Column(verticalArrangement = Arrangement.spacedBy(PleosSpacing.space12)) {
                    RecommendedPlacePhoto(wide)
                    Column(verticalArrangement = Arrangement.spacedBy(PleosSpacing.space4)) {
                        Text(
                            text = stringResource(R.string.journey_home_place),
                            style = PleosTextStyles.section,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = stringResource(R.string.journey_home_place_district),
                            style = PleosTextStyles.meta,
                            color = colors.textSecondary,
                        )
                    }
                    if (onOpenMap != null) {
                        PleosButton(
                            text = stringResource(R.string.journey_open_map),
                            onClick = onOpenMap,
                            modifier = Modifier.fillMaxWidth(),
                            showArrow = true,
                        )
                    }
                    PleosTextLink(stringResource(R.string.journey_start), onStartJourney)
                }
            },
            side = { wide ->
                PleosInfoPanel(Modifier.fillMaxWidth(), outline = colors.borderControl) {
                    Text(
                        text = stringResource(R.string.journey_home_collection_title),
                        style = PleosTextStyles.section,
                        color = colors.textPrimary,
                    )
                    if (wide) {
                        MilestoneCard(
                            face = MilestoneCardFace.CommonBack,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                .padding(vertical = PleosSpacing.space8)
                                .width(120.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.journey_home_collection_body),
                        style = PleosTextStyles.meta,
                        color = colors.textSecondary,
                    )
                    PleosTextLink(stringResource(R.string.journey_collection), onOpenBadges)
                }
            },
        )
    }
}

@Composable
internal fun ParkedJourneySummary(
    summary: JourneySummary?,
    onReturnHome: () -> Unit,
    onStartJourney: () -> Unit,
    onOpenBadges: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PleosTheme.colors
    PleosScreen(title = stringResource(R.string.journey_finished_summary), modifier = modifier) {
        Text(
            text = stringResource(R.string.journey_finished),
            style = PleosTextStyles.meta,
            color = colors.actionPrimary,
        )
        ParkedJourneyColumns(
            primaryWeight = 1f,
            primary = {
                PleosInfoPanel(Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.journey_summary_title),
                        style = PleosTextStyles.section,
                        color = colors.textPrimary,
                    )
                    HorizontalDivider(color = colors.borderDefault)
                    Text(
                        text = stringResource(R.string.journey_summary_duration),
                        style = PleosTextStyles.meta,
                        color = colors.textSecondary,
                    )
                    if (summary == null) {
                        Text(
                            text = stringResource(R.string.journey_summary_duration_unavailable),
                            style = PleosTextStyles.body,
                            color = colors.textSecondary,
                        )
                    } else {
                        Text(
                            text = journeyDuration(summary.durationMillis),
                            style = PleosTextStyles.titleSplit,
                            color = colors.textPrimary,
                        )
                    }
                }
            },
            side = {
                Column(verticalArrangement = Arrangement.spacedBy(PleosSpacing.space12)) {
                    Text(
                        text = stringResource(R.string.journey_summary_next),
                        style = PleosTextStyles.body,
                        color = colors.textSecondary,
                    )
                    PleosButton(
                        text = stringResource(R.string.journey_return_home),
                        onClick = onReturnHome,
                        modifier = Modifier.fillMaxWidth(),
                        showArrow = true,
                    )
                    PleosTextLink(stringResource(R.string.journey_start_another), onStartJourney)
                    PleosTextLink(stringResource(R.string.journey_collection), onOpenBadges)
                }
            },
        )
    }
}

@Composable
private fun ParkedJourneyColumns(
    primaryWeight: Float,
    primary: @Composable (Boolean) -> Unit,
    side: @Composable (Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 680.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.space24)) {
                Box(Modifier.weight(primaryWeight)) { primary(true) }
                Box(Modifier.weight(1f)) { side(true) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(PleosSpacing.space12)) {
                primary(false)
                side(false)
            }
        }
    }
}

@Composable
private fun RecommendedPlacePhoto(wide: Boolean) {
    val colors = PleosTheme.colors
    val shape = PleosShape.button
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(if (wide) 2.45f else 3.6f).clip(shape)
            .border(2.dp, Brush.linearGradient(listOf(colors.actionPrimary, colors.accentLilac)), shape),
    ) {
        Image(
            painter = painterResource(DesignR.drawable.milestone_art_haeundae),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(PleosSpacing.space4).clip(shape),
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(horizontalBias = 0f, verticalBias = -0.35f),
        )
        Text(
            text = stringResource(R.string.journey_home_place_category),
            modifier = Modifier.align(Alignment.BottomStart).padding(PleosSpacing.space16)
                .background(colors.background.copy(alpha = .88f), PleosShape.chip)
                .padding(horizontal = PleosSpacing.space12, vertical = PleosSpacing.space4),
            style = PleosTextStyles.meta,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun journeyDuration(durationMillis: Long): String {
    val seconds = durationMillis / 1_000
    val minutes = seconds / 60
    return when {
        minutes >= 60 -> stringResource(R.string.journey_duration_hours_minutes, minutes / 60, minutes % 60)
        minutes > 0 -> stringResource(R.string.journey_duration_minutes_seconds, minutes, seconds % 60)
        else -> stringResource(R.string.journey_duration_seconds, seconds)
    }
}
