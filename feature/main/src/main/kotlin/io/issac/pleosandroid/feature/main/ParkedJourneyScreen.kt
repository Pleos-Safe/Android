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

@Composable
internal fun ParkedTravelHome(
    place: HomePlace? = null,
    onOpenBadges: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenMap: (() -> Unit)? = null,
    onOpenRecords: () -> Unit = {},
    pointBalance: Long? = null,
    demoWallet: Boolean = false,
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
            primaryWeight = 1.35f,
            primary = { wide ->
                Column(verticalArrangement = Arrangement.spacedBy(PleosLayout.itemGap)) {
                    RecommendedPlacePhoto(wide, place)
                    Column(verticalArrangement = Arrangement.spacedBy(PleosSpacing.space4 * PleosTextStyles.scale)) {
                        Text(
                            text = place?.name ?: "여행지를 불러오고 있어요",
                            style = PleosTextStyles.section,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = place?.district.orEmpty(),
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
                }
            },
            side = { _ ->
                PleosInfoPanel(Modifier.fillMaxWidth(), outline = colors.borderControl) {
                    Text(
                        text = stringResource(R.string.journey_home_collection_title),
                        style = PleosTextStyles.section,
                        color = colors.textPrimary,
                    )
                    if (pointBalance != null) {
                        Text(
                            if (demoWallet) "데모 강화 포인트" else "보유 강화 포인트",
                            style = PleosTextStyles.meta,
                            color = colors.textSecondary,
                        )
                        Text(
                            pointsLabel(pointBalance),
                            style = PleosTextStyles.titleSplit,
                            color = colors.actionPrimary,
                        )
                    }
                    LocalHomeCard.current()
                    Text(
                        text = stringResource(R.string.journey_home_collection_body),
                        style = PleosTextStyles.meta,
                        color = colors.textSecondary,
                    )
                    PleosButton(
                        stringResource(R.string.journey_collection),
                        onOpenBadges,
                        Modifier.fillMaxWidth(),
                        style = PleosButtonStyle.Secondary,
                        showArrow = true,
                    )
                    PleosTextLink("운전습관 기록", onOpenRecords)
                }
            },
        )
    }
}

@Composable
internal fun ParkedJourneyPause(
    progress: JourneyProgressDisplay?,
    recovering: Boolean,
    pausedAtNanos: Long?,
    busy: Boolean,
    error: Boolean,
    onContinueJourney: () -> Unit,
    onFinishJourney: () -> Unit,
    modifier: Modifier = Modifier,
    visitReward: @Composable () -> Unit = {},
) {
    var confirming by rememberSaveable(pausedAtNanos) { mutableStateOf(false) }
    var readyToResume by rememberSaveable(pausedAtNanos) { mutableStateOf(false) }
    val colors = PleosTheme.colors
    PleosScreen(title = if (recovering) "이전 주행 이어가기" else "잠시 주차했어요", modifier = modifier) {
        ParkedJourneyColumns(
            primaryWeight = 1.1f,
            primary = {
                Column(verticalArrangement = Arrangement.spacedBy(PleosLayout.itemGap)) {
                    PleosInfoPanel(Modifier.fillMaxWidth()) {
                        Text(
                            if (recovering) "지난 주행 기록이 남아 있어요" else "휴식 중에도 기록을 보관해요",
                            style = PleosTextStyles.section,
                            color = colors.textPrimary,
                        )
                        Text(
                            if (readyToResume) "다시 출발하면 이어서 기록해요." else "쉬었다가 이어서 달리거나 여기서 주행을 마칠 수 있어요.",
                            style = PleosTextStyles.body,
                            color = colors.textSecondary,
                        )
                    }
                    if (!recovering) visitReward()
                }
            },
            side = {
                Column(verticalArrangement = Arrangement.spacedBy(PleosLayout.itemGap)) {
                    if (progress != null) {
                        PleosInfoPanel(Modifier.fillMaxWidth()) {
                            JourneyProgressRows(progress)
                        }
                    }
                    if (error) {
                        Text("처리하지 못했어요. 차량 상태를 확인하고 다시 시도해 주세요.", color = colors.statusPending)
                    }
                    PleosButton(
                        "주행 계속하기",
                        onClick = {
                            if (recovering) {
                                onContinueJourney()
                            } else {
                                readyToResume = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && !readyToResume,
                        showArrow = true,
                    )
                    PleosButton(
                        "주행 종료하기",
                        onClick = { confirming = true },
                        modifier = Modifier.fillMaxWidth(),
                        style = PleosButtonStyle.Secondary,
                        enabled = !busy,
                        showArrow = true,
                    )
                }
            },
        )
    }
    if (confirming) {
        PleosDialog(
            title = "이번 주행을 마무리할까요?",
            message = "기록된 주행 거리와 실제 주행 시간을 바탕으로 강화 포인트를 정산해요.",
            confirmLabel = "주행 종료하기",
            dismissLabel = "계속 기록하기",
            onConfirm = {
                confirming = false
                onFinishJourney()
            },
            onDismissRequest = { confirming = false },
        )
    }
}

@Composable
internal fun JourneyProgressRows(progress: JourneyProgressDisplay) {
    JourneyMetricRow("기록한 거리", journeyDistance(progress.distanceMeters))
    HorizontalDivider(color = PleosTheme.colors.borderDefault)
    JourneyMetricRow("실제 주행 시간", journeyDuration(progress.movingMillis))
}

@Composable
private fun JourneyMetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = PleosTextStyles.meta, color = PleosTheme.colors.textSecondary)
        Text(value, style = PleosTextStyles.body, color = PleosTheme.colors.textPrimary)
    }
}

internal fun pointsLabel(halfPoints: Long): String = formatHalfPoints(halfPoints)

@Composable
internal fun journeyDistance(meters: Long): String = stringResource(R.string.journey_distance_km, meters / 1_000.0)

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
                    Text(
                        text = stringResource(R.string.journey_summary_next),
                        style = PleosTextStyles.body,
                        color = colors.textSecondary,
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

                                else -> "저장된 주행 기록은 운전습관 탭에서 확인할 수 있어요."
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

@Composable
private fun ParkedJourneyColumns(
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
private fun RecommendedPlacePhoto(wide: Boolean, place: HomePlace?) {
    val context = LocalContext.current
    val imageId = remember(place?.imageKey, context) {
        place?.imageKey?.let { context.resources.getIdentifier(it, "drawable", context.packageName) } ?: 0
    }
    val colors = PleosTheme.colors
    val shape = PleosShape.button
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(if (wide) 1.7f else 2.05f).clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.borderControl, shape),
    ) {
        if (imageId != 0) {
            Image(
                painter = painterResource(imageId),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(horizontalBias = 0f, verticalBias = -0.35f),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(androidx.compose.ui.graphics.Color.Transparent, colors.background.copy(alpha = .65f)),
                ),
            ),
        )
        Text(
            text = place?.category.orEmpty(),
            modifier = Modifier.align(Alignment.BottomStart).padding(PleosLayout.panelPadding)
                .background(colors.background.copy(alpha = .88f), PleosShape.chip)
                .padding(horizontal = PleosSpacing.space12, vertical = PleosSpacing.space4),
            style = PleosTextStyles.meta,
            color = colors.textPrimary,
        )
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
