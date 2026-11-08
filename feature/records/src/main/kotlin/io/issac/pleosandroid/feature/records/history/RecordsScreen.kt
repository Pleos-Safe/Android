package io.issac.pleosandroid.feature.records.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosButtonStyle
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.theme.PleosShape
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.repository.VisitRecordingStatus
import io.issac.pleosandroid.core.domain.driving.repository.RecordingPhase
import io.issac.pleosandroid.feature.records.component.ObservationRow
import io.issac.pleosandroid.feature.records.component.QuietPlate
import io.issac.pleosandroid.feature.records.component.RecordBody
import io.issac.pleosandroid.feature.records.component.RecordCaption
import io.issac.pleosandroid.feature.records.component.RecordHeading
import io.issac.pleosandroid.feature.records.component.RecordIcon
import io.issac.pleosandroid.feature.records.component.RecordSymbol
import io.issac.pleosandroid.feature.records.component.RecordsPage
import io.issac.pleosandroid.feature.records.component.dateLabel
import io.issac.pleosandroid.feature.records.component.distanceLabel
import io.issac.pleosandroid.feature.records.component.timeLabel
import io.issac.pleosandroid.feature.records.points.RecordPoints
import io.issac.pleosandroid.feature.records.points.journeyPointsLabel
import io.issac.pleosandroid.feature.records.points.pointLabel

@Composable
internal fun RecordsScreen(
    state: RecordsState,
    onIntent: (RecordsIntent) -> Unit,
    onTravel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = PleosTextStyles.scale
    RecordsPage("주행 기록", modifier, "다녀온 여정과 쌓인 포인트를 확인해요.") {
        when {
            state.loading -> RecordBody("기록을 불러오고 있어요.")

            state.error -> {
                RecordBody("기록을 불러오지 못했어요.")
                PleosButton("다시 시도", { onIntent(RecordsIntent.Retry) })
            }

            else -> {
                if (state.demo) RecordCaption("POC · 데모 기록", accent = true)
                RecordingStatus(state, onIntent)
                RecordsOverview(state)
                PointsOverview(state, onIntent)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RecordHeading("최근 주행")
                    PleosTextLink("저장 설정", { onIntent(RecordsIntent.Settings) })
                }
                val latest = state.journeys.firstOrNull()
                if (latest == null) {
                    QuietPlate {
                        RecordIcon(RecordSymbol.Bookmark)
                        RecordHeading("아직 남긴 여정이 없어요")
                        RecordBody("저장을 켜면 다음 여정부터 남겨요. 저장하지 않아도 장소를 둘러볼 수 있어요.")
                        PleosButton("장소 둘러보기", onTravel, showArrow = true, style = PleosButtonStyle.Secondary)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp * scale)) {
                        var requestedPage by rememberSaveable { mutableIntStateOf(0) }
                        val lastPage = (state.journeys.size - 1) / RECORDS_PER_PAGE
                        val page = requestedPage.coerceIn(0, lastPage)
                        PleosTextLink("운전습관 보기", { onIntent(RecordsIntent.OpenJourney(latest.id)) })
                        state.journeys.drop(page * RECORDS_PER_PAGE).take(RECORDS_PER_PAGE).forEach { journey ->
                            ObservationRow(
                                journey.dateLabel(),
                                "${journey.timeLabel()} · ${journey.distanceLabel()}",
                                journeyPointsLabel(journey, state.points, state.demo),
                                onClick = { onIntent(RecordsIntent.OpenJourney(journey.id)) },
                            )
                        }
                        if (lastPage > 0) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PleosTextLink("이전", { requestedPage = page - 1 }, enabled = page > 0)
                                RecordCaption("${page + 1} / ${lastPage + 1}")
                                PleosTextLink("다음", { requestedPage = page + 1 }, enabled = page < lastPage)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordsOverview(state: RecordsState) {
    val scale = PleosTextStyles.scale
    val score = state.journeys.firstNotNullOfOrNull { journey ->
        if (journey.assessment == AssessmentStatus.Complete) journey.score else null
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        RecordStat("함께한 주행", "${state.journeys.size}회", Modifier.weight(1f))
        RecordStat("방문한 장소", "${state.visitedPlaceCount}곳", Modifier.weight(1f))
        RecordStat("최근 안전 점수", score?.let { "$it 점" } ?: "—", Modifier.weight(1f))
    }
}

@Composable
private fun RecordStat(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = PleosTheme.colors
    val scale = PleosTextStyles.scale
    Column(
        modifier.heightIn(min = 92.dp * scale)
            .background(colors.surface, PleosShape.inset)
            .border(1.dp, colors.borderDefault, PleosShape.inset)
            .padding(12.dp * scale),
        verticalArrangement = Arrangement.spacedBy(4.dp * scale, Alignment.CenterVertically),
    ) {
        RecordCaption(label)
        RecordHeading(value)
    }
}

@Composable
private fun PointsOverview(state: RecordsState, onIntent: (RecordsIntent) -> Unit) {
    val label = when {
        state.points is RecordPoints.Ready -> {
            val label = if (state.demo) "데모 포인트" else "보유 포인트"
            "$label ${state.points.wallet.balanceHalfPoints.pointLabel()}"
        }

        state.points is RecordPoints.Loading -> "포인트를 확인하고 있어요."

        else -> "포인트를 불러오지 못했어요."
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp * PleosTextStyles.scale),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecordCaption(label, Modifier.weight(1f), accent = state.points is RecordPoints.Ready && !state.demo)
        PleosTextLink("이번 주 포인트", { onIntent(RecordsIntent.OpenPointsWeek) })
    }
}

private const val RECORDS_PER_PAGE = 4

@Composable
internal fun RecordingStatus(state: RecordsState, onIntent: (RecordsIntent) -> Unit) {
    val phase = state.recording.phase
    if (phase == RecordingPhase.Unavailable) {
        QuietPlate {
            RecordHeading("기록 서비스를 시작하지 못했어요")
            RecordCaption("지금은 새 여정을 측정하지 않아요. 주차 중 앱을 다시 열어 주세요.")
        }
    }
    if (phase == RecordingPhase.Recording || phase == RecordingPhase.Saving || phase == RecordingPhase.Error) {
        QuietPlate {
            RecordHeading(
                when (phase) {
                    RecordingPhase.Recording -> "이번 여정을 기록하고 있어요"
                    RecordingPhase.Saving -> "이번 여정을 저장하고 있어요"
                    else -> "기록 저장을 확인해 주세요"
                },
            )
            if (phase == RecordingPhase.Recording) {
                RecordCaption("P단에서는 기록을 잠시 보관해요. 주행을 종료하면 저장해요.")
            }
            if (phase ==
                RecordingPhase.Error
            ) {
                PleosButton("저장 다시 시도", { onIntent(RecordsIntent.RetrySave) }, enabled = !state.busy)
            }
        }
    }
    if (state.visitStatus == VisitRecordingStatus.StorageError) {
        RecordCaption("방문 기록을 저장하지 못했어요. 저장소를 확인한 뒤 다시 시도해요.", accent = true)
    }
    state.message?.let { RecordCaption(it) }
}
