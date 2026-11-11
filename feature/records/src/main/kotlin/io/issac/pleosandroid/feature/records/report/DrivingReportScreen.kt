package io.issac.pleosandroid.feature.records.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.feature.records.component.ObservationRow
import io.issac.pleosandroid.feature.records.component.QuietPlate
import io.issac.pleosandroid.feature.records.component.RecordBody
import io.issac.pleosandroid.feature.records.component.RecordCaption
import io.issac.pleosandroid.feature.records.component.RecordColumns
import io.issac.pleosandroid.feature.records.component.RecordDivider
import io.issac.pleosandroid.feature.records.component.RecordHeading
import io.issac.pleosandroid.feature.records.component.RecordIcon
import io.issac.pleosandroid.feature.records.component.RecordSymbol
import io.issac.pleosandroid.feature.records.component.RecordsPage
import io.issac.pleosandroid.feature.records.component.dateLabel
import io.issac.pleosandroid.feature.records.component.distanceLabel
import io.issac.pleosandroid.feature.records.component.durationLabel
import io.issac.pleosandroid.feature.records.component.recordDurationLabel
import io.issac.pleosandroid.feature.records.component.timeLabel
import io.issac.pleosandroid.feature.records.points.JourneyPointsCard
import io.issac.pleosandroid.feature.records.points.RecordPoints

@Composable
internal fun DrivingReportScreen(
    journey: JourneyRecord,
    demo: Boolean,
    onBack: () -> Unit,
    onDataStatus: () -> Unit,
    onCollection: () -> Unit,
    modifier: Modifier = Modifier,
    points: RecordPoints = RecordPoints.Loading,
    onPointsRetry: () -> Unit = {},
) {
    if (journey.assessment != AssessmentStatus.Complete || journey.score == null) {
        DrivingDataStatusScreen(
            journey,
            demo,
            onBack,
            onReport = null,
            modifier = modifier,
            points = points,
            onPointsRetry = onPointsRetry,
        )
        return
    }
    val scale = PleosTextStyles.scale
    val subtitle = journey.dateLabel() + if (demo) " · 체험 기록" else ""
    RecordsPage("이번 여정의 운전습관", modifier, subtitle, onBack) { wide ->
        RecordColumns(
            wide,
            leading = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                    if (wide) {
                        QuietPlate(Modifier.heightIn(min = 390.dp * scale)) {
                            RecordIcon(RecordSymbol.Shield)
                            ReportIntroduction(journey)
                            RecordDivider()
                            Score(journey)
                            RecordCaption("점수는 가속 흐름을 돌아보는 참고 정보예요.\n안전 인증이나 사고 위험을 뜻하지 않아요.")
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RecordIcon(RecordSymbol.Shield)
                            RecordHeading(reportTitle(journey))
                        }
                        QuietPlate(contentPadding = 16.dp) { Score(journey, compact = true) }
                    }
                    JourneyPointsCard(journey, points, demo, onPointsRetry)
                }
            },
            trailing = {
                Column(verticalArrangement = Arrangement.spacedBy((if (wide) 12.dp else 8.dp) * scale)) {
                    RecordHeading("오늘의 운전습관")
                    JourneyTravelFacts(journey)
                    val data = journey.driving
                    ObservationRow(
                        "급한 가속",
                        when {
                            data == null -> "이 기록에는 상세 관찰 정보가 없어요."
                            data.harshAccelerations == 0 -> "지속된 급가속 구간이 관찰되지 않았어요."
                            else -> "속도를 빠르게 높인 구간이 관찰됐어요."
                        },
                        data?.let { "${it.harshAccelerations}회" } ?: "정보 없음",
                    )
                    ObservationRow(
                        "갑작스러운 감속",
                        if (data == null) {
                            "이 기록에는 상세 관찰 정보가 없어요."
                        } else {
                            "도로 상황을 알 수 없어 횟수만 안내해요. 점수에서는 제외해요."
                        },
                        data?.let { "${it.harshBrakings}회" } ?: "정보 없음",
                    )
                    if (wide) {
                        ObservationRow(
                            "측정 구간",
                            if (data != null) {
                                "여정의 ${(data.coverage * 100).toInt()}%를 관찰했어요."
                            } else {
                                "상세 측정 정보는 보관되지 않았어요."
                            },
                            if (data != null) "확인됨" else "정보 없음",
                        )
                    }
                    if (!wide) RecordCaption("가속 흐름의 참고 점수이며, 안전 인증이나 사고 위험을 뜻하지 않아요.")
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp * scale),
                        verticalArrangement = Arrangement.spacedBy(8.dp * scale),
                    ) {
                        PleosTextLink("측정 상세", onDataStatus)
                        PleosTextLink("카드 컬렉션", onCollection)
                    }
                }
            },
        )
    }
}

@Composable
internal fun JourneyTravelFacts(journey: JourneyRecord) {
    val activeTime = journey.driving?.movingMillis?.recordDurationLabel() ?: "정보 없음"
    RecordCaption("${journey.timeLabel()} · 기록 시간 ${journey.durationLabel()}")
    RecordCaption("관찰 거리 ${journey.distanceLabel()} · 이동 시간 $activeTime")
}

private fun reportTitle(journey: JourneyRecord): String = when {
    journey.driving == null -> "여정을 돌아보는 시간"
    journey.driving?.harshAccelerations == 0 -> "차분하게 마친 여정"
    else -> "오늘의 흐름을 돌아봐요"
}

@Composable
private fun ReportIntroduction(journey: JourneyRecord) {
    RecordHeading(reportTitle(journey))
    RecordBody("이번 기록에서 살펴본\n운전습관이에요.")
}

@Composable
private fun Score(journey: JourneyRecord, compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RecordCaption("참고 점수 · 가속 흐름")
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                journey.score.toString(),
                style = PleosTextStyles.titleFull.copy(
                    fontSize = (if (compact) 34.sp else 44.sp) * PleosTextStyles.scale,
                    lineHeight = (if (compact) 42.sp else 54.sp) * PleosTextStyles.scale,
                    fontWeight = FontWeight.Medium,
                ),
                color = PleosTheme.colors.textPrimary,
            )
            RecordCaption("점", Modifier.padding(bottom = 6.dp))
        }
        if (!compact) RecordCaption("점수는 기록된 주행 구간을 기준으로 계산해요.")
    }
}
