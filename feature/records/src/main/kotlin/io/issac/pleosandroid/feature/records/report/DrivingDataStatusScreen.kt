package io.issac.pleosandroid.feature.records.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.driving.model.InsufficientReason
import io.issac.pleosandroid.feature.records.component.ObservationRow
import io.issac.pleosandroid.feature.records.component.QuietPlate
import io.issac.pleosandroid.feature.records.component.RecordBody
import io.issac.pleosandroid.feature.records.component.RecordCaption
import io.issac.pleosandroid.feature.records.component.RecordColumns
import io.issac.pleosandroid.feature.records.component.RecordHeading
import io.issac.pleosandroid.feature.records.component.RecordIcon
import io.issac.pleosandroid.feature.records.component.RecordSymbol
import io.issac.pleosandroid.feature.records.component.RecordsPage
import io.issac.pleosandroid.feature.records.component.dateLabel
import io.issac.pleosandroid.feature.records.component.recordDurationLabel
import io.issac.pleosandroid.feature.records.points.JourneyPointsCard
import io.issac.pleosandroid.feature.records.points.RecordPoints

@Composable
internal fun DrivingDataStatusScreen(
    journey: JourneyRecord,
    onBack: () -> Unit,
    onReport: (() -> Unit)?,
    modifier: Modifier = Modifier,
    points: RecordPoints = RecordPoints.Loading,
    onPointsRetry: () -> Unit = {},
) {
    val complete = journey.assessment == AssessmentStatus.Complete && journey.score != null
    val data = journey.driving
    val scale = PleosTextStyles.scale
    RecordsPage("운전습관 기록 상태", modifier, journey.dateLabel(), onBack) { wide ->
        RecordColumns(
            wide,
            leading = {
                QuietPlate(Modifier.heightIn(min = if (wide) 343.dp * scale else 200.dp * scale)) {
                    RecordIcon(if (complete) RecordSymbol.Check else RecordSymbol.Pause)
                    Spacer(Modifier.height(12.dp * scale))
                    RecordHeading(if (complete) "살펴볼 기록이 모였어요" else "분석 가능한 기록이\n아직 부족해요")
                    RecordBody(
                        if (complete) {
                            "여정이 끝났고, 이번 기록으로\n운전습관을 돌아볼 수 있어요."
                        } else {
                            "이번 주행은 기록으로 남겼어요.\n측정 정보가 부족해 점수를 내지 않았어요."
                        },
                    )
                }
            },
            trailing = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                    RecordHeading("이번 기록")
                    JourneyTravelFacts(journey)
                    ObservationRow(
                        "운전 정보",
                        data?.let { "측정 시간 ${it.observedMillis.recordDurationLabel()}" }
                            ?: "이전 기록에는 상세 측정 정보가 없어요.",
                        data?.let { "${(it.coverage * 100).toInt()}%" } ?: "정보 없음",
                    )
                    if (!complete) {
                        val reasons = data?.reasons.orEmpty()
                        RecordBody(
                            if (reasons.isEmpty()) {
                                "여정 전체를 살펴볼 운전 정보가 충분하지 않아요."
                            } else {
                                reasons.map(::reasonText).distinct().joinToString("\n")
                            },
                        )
                    } else {
                        RecordCaption("차량 속도의 변화만 관찰해요. 도로 제한속도, 교통 상황, 급회전 여부는 판단하지 않아요.")
                        RecordCaption("갑작스러운 감속은 필요한 대응일 수 있어 점수에서 제외해요.")
                    }
                    PleosButton(
                        if (complete && onReport != null) "리포트 보기" else "기록으로 돌아가기",
                        onClick = if (complete && onReport != null) onReport else onBack,
                        modifier = Modifier.fillMaxWidth(),
                        showArrow = true,
                    )
                }
            },
        )
        if (!complete) JourneyPointsCard(journey, points, onPointsRetry)
    }
}

private fun reasonText(reason: InsufficientReason): String = when (reason) {
    InsufficientReason.TooShort, InsufficientReason.TooLittleDistance, InsufficientReason.TooFewSamples ->
        "이번 여정은 분석할 수 있는 구간이 짧았어요."

    InsufficientReason.LowCoverage, InsufficientReason.SignalGap -> "차량 정보를 받지 못한 구간이 있어요."

    InsufficientReason.InvalidSignal -> "일부 차량 정보가 유효하지 않았어요."

    InsufficientReason.StartedWhileMoving -> "출발 이후에 기록이 시작되어 여정 전체를 확인하지 못했어요."

    InsufficientReason.Interrupted -> "기록이 중단되어 마지막 저장 지점까지만 남겼어요."
}
