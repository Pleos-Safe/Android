package io.issac.pleosandroid.feature.records.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.repository.VisitRecordingStatus
import io.issac.pleosandroid.core.domain.driving.repository.RecordingPhase
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
import io.issac.pleosandroid.feature.records.component.timeLabel

@Composable
internal fun RecordsScreen(
    state: RecordsState,
    onIntent: (RecordsIntent) -> Unit,
    onTravel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = PleosTextStyles.scale
    RecordsPage("여정 기록", modifier, "다녀온 날들이 한 장씩 남아요.") { wide ->
        when {
            state.loading -> RecordBody("기록을 불러오고 있어요.")

            state.error -> {
                RecordBody("기록을 불러오지 못했어요.")
                PleosButton("다시 시도", { onIntent(RecordsIntent.Retry) })
            }

            else -> {
                if (state.demo) RecordCaption("POC · 데모 기록", accent = true)
                RecordingStatus(state, onIntent)
                val latest = state.journeys.firstOrNull()
                if (latest == null) {
                    RecordColumns(
                        wide,
                        leading = {
                            QuietPlate(Modifier.heightIn(min = if (wide) 330.dp * scale else 250.dp * scale)) {
                                RecordIcon(RecordSymbol.Bookmark)
                                Spacer(Modifier.height(20.dp * scale))
                                RecordHeading("아직 남긴 여정이 없어요")
                                RecordBody("마음에 남은 장소와 돌아온 하루를\n차분히 모아둘 수 있어요.")
                            }
                        },
                        trailing = {
                            Column(verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
                                RecordHeading("기록은 내가 선택해요")
                                RecordBody("저장을 켜면 그다음 여정부터 남겨요.\n저장하지 않아도 장소를 둘러볼 수 있어요.")
                                PleosButton("장소 둘러보기", onTravel, Modifier.fillMaxWidth(), showArrow = true)
                                PleosButton("저장 설정", {
                                    onIntent(RecordsIntent.Settings)
                                }, Modifier.fillMaxWidth(), style = PleosButtonStyle.Secondary)
                            }
                        },
                    )
                } else {
                    RecordColumns(
                        wide,
                        leading = {
                            QuietPlate(Modifier.heightIn(min = if (wide) 365.dp * scale else 260.dp * scale)) {
                                RecordIcon(RecordSymbol.Shield)
                                RecordCaption("가장 최근에 남긴 여정", accent = true)
                                RecordHeading(latest.dateLabel())
                                RecordBody(latest.timeLabel())
                                RecordCaption("${latest.durationLabel()} · 관찰 거리 ${latest.distanceLabel()}")
                                RecordDivider()
                                RecordHeading(
                                    if (latest.assessment == AssessmentStatus.Complete) {
                                        "오늘의 운전습관을 돌아봐요"
                                    } else {
                                        "이번 여정의 기록을 남겼어요"
                                    },
                                )
                                RecordCaption(
                                    if (latest.assessment == AssessmentStatus.Complete) {
                                        "주차한 뒤 천천히 살펴보세요."
                                    } else {
                                        "분석할 정보가 부족해 점수는 보류했어요."
                                    },
                                )
                                PleosButton("운전습관 보기", {
                                    onIntent(RecordsIntent.OpenJourney(latest.id))
                                }, Modifier.fillMaxWidth(), showArrow = true)
                            }
                        },
                        trailing = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                                RecordHeading("함께한 여정 · ${state.journeys.size}개")
                                var requestedPage by rememberSaveable { mutableIntStateOf(0) }
                                val lastPage = (state.journeys.size - 1) / 4
                                val page = requestedPage.coerceIn(0, lastPage)
                                state.journeys.drop(page * 4).take(4).forEach { journey ->
                                    ObservationRow(
                                        journey.dateLabel(),
                                        "${journey.timeLabel()} · ${journey.durationLabel()}",
                                        if (journey.assessment == AssessmentStatus.Complete) "리포트 →" else "기록 부족 →",
                                        onClick = { onIntent(RecordsIntent.OpenJourney(journey.id)) },
                                    )
                                }
                                if (lastPage >
                                    0
                                ) {
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
                                PleosTextLink("저장 설정", { onIntent(RecordsIntent.Settings) })
                            }
                        },
                    )
                }
            }
        }
    }
}

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
                RecordCaption("정지 후 P 기어로 전환하면 이번 여정을 저장해요.")
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
