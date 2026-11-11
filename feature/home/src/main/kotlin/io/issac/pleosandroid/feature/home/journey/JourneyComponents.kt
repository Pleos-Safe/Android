package io.issac.pleosandroid.feature.home.journey

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosButtonStyle
import io.issac.pleosandroid.core.designsystem.component.PleosDialog
import io.issac.pleosandroid.core.designsystem.component.PleosInfoPanel
import io.issac.pleosandroid.core.designsystem.component.PleosScreen
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosShape
import io.issac.pleosandroid.core.designsystem.theme.PleosSpacing
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.driving.repository.RecordingPhase
import io.issac.pleosandroid.core.domain.points.formatHalfPoints
import io.issac.pleosandroid.feature.home.HomePlace
import io.issac.pleosandroid.feature.home.JourneyProgressDisplay
import io.issac.pleosandroid.feature.home.JourneySummary
import io.issac.pleosandroid.feature.home.LocalHomeCard
import io.issac.pleosandroid.feature.home.R

@Composable
internal fun JourneyProgressRows(progress: JourneyProgressDisplay) {
    JourneyMetricRow("기록한 거리", journeyDistance(progress.distanceMeters))
    HorizontalDivider(color = PleosTheme.colors.borderDefault)
    JourneyMetricRow("실제 주행 시간", journeyDuration(progress.movingMillis))
}

@Composable
internal fun JourneyMetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PleosLayout.itemGap)) {
        Text(label, Modifier.weight(1f), style = PleosTextStyles.meta, color = PleosTheme.colors.textSecondary)
        Text(value, style = PleosTextStyles.body, color = PleosTheme.colors.textPrimary)
    }
}

internal fun pointsLabel(halfPoints: Long): String = formatHalfPoints(halfPoints)

@Composable
internal fun journeyDistance(meters: Long): String = stringResource(R.string.journey_distance_km, meters / 1_000.0)

@Composable
internal fun ParkedJourneyColumns(
    primaryWeight: Float,
    primary: @Composable (Boolean) -> Unit,
    side: @Composable (Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= PleosLayout.twoPaneMinWidth) {
            Row(horizontalArrangement = Arrangement.spacedBy(PleosLayout.columnGap)) {
                Box(Modifier.weight(primaryWeight)) { primary(true) }
                Box(Modifier.weight(1f)) { side(true) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(PleosLayout.sectionGap)) {
                primary(false)
                side(false)
            }
        }
    }
}

@Composable
internal fun journeyDuration(durationMillis: Long): String {
    val seconds = durationMillis / 1_000
    val minutes = seconds / 60
    return when {
        minutes >= 60 -> stringResource(R.string.journey_duration_hours_minutes, minutes / 60, minutes % 60)
        minutes > 0 -> stringResource(R.string.journey_duration_minutes_seconds, minutes, seconds % 60)
        else -> stringResource(R.string.journey_duration_seconds, seconds)
    }
}
