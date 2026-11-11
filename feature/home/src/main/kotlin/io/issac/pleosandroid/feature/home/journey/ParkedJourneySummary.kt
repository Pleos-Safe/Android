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
internal fun ParkedJourneySummary(
    summary: JourneySummary?,
    onOpenRecords: () -> Unit = {},
    onOpenReport: (String) -> Unit = {},
    onReturnHome: () -> Unit,
    onOpenBadges: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PleosTheme.colors
    PleosScreen(
        title = if (summary?.earnedPoints == 0L) "주행 기록 저장" else "주행 완료",
        modifier = modifier,
    ) {
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
                    summary?.earnedPoints?.let { points ->
                        Text(
                            if (points > 0) "이번 주행의 강화 포인트" else "이번 주행은 기록만 남겼어요",
                            style = PleosTextStyles.meta,
                            color = colors.textSecondary,
                        )
                        Text(
                            (if (points > 0) "+" else "") + pointsLabel(points),
                            style = PleosTextStyles.titleSplit,
                            color = colors.actionPrimary,
                        )
                        if (summary.membershipBonusHalfPoints > 0) {
                            JourneyMetricRow("PLUS 추가 적립", "+" + pointsLabel(summary.membershipBonusHalfPoints))
                        }
                        summary.settlementReason?.let {
                            Text(it, style = PleosTextStyles.meta, color = colors.textSecondary)
                        }
                        summary.safetyScore?.let { JourneyMetricRow("안전점수", "${it}점") }
                        JourneyMetricRow("포인트 인정 거리", journeyDistance(summary.recognizedDistanceMeters ?: 0L))
                        HorizontalDivider(color = colors.borderDefault)
                    }
                    summary?.distanceMeters?.let { JourneyMetricRow("전체 거리", journeyDistance(it)) }
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
                Column(verticalArrangement = Arrangement.spacedBy(PleosLayout.sectionGap)) {
                    PleosButton(
                        text = stringResource(R.string.journey_return_home),
                        onClick = onReturnHome,
                        modifier = Modifier.fillMaxWidth(),
                        showArrow = true,
                    )
                    val reportId = summary?.reportId
                    if (reportId != null) {
                        PleosTextLink("이번 여정 운전습관 보기", { onOpenReport(reportId) })
                    } else {
                        Text(
                            text = when (summary?.recordingPhase) {
                                RecordingPhase.Saving,
                                RecordingPhase.Recording,
                                -> "주행 기록을 저장하고 있어요."

                                RecordingPhase.Error -> "기록에서 저장을 다시 시도할 수 있어요."

                                RecordingPhase.Disabled -> "주행 기록 저장이 꺼져 있어요."

                                else -> "저장한 여정은 기록 탭에서 볼 수 있어요."
                            },
                            style = PleosTextStyles.meta,
                            color = colors.textSecondary,
                        )
                    }
                    PleosTextLink(stringResource(R.string.journey_collection), onOpenBadges)
                    PleosTextLink("운전습관 기록", onOpenRecords)
                }
            },
        )
    }
}
