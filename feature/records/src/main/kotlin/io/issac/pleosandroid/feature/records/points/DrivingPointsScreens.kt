package io.issac.pleosandroid.feature.records.points

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.points.PointEarningPolicy
import io.issac.pleosandroid.core.domain.points.PointWallet
import io.issac.pleosandroid.feature.records.component.ObservationRow
import io.issac.pleosandroid.feature.records.component.QuietPlate
import io.issac.pleosandroid.feature.records.component.RecordBody
import io.issac.pleosandroid.feature.records.component.RecordCaption
import io.issac.pleosandroid.feature.records.component.RecordColumns
import io.issac.pleosandroid.feature.records.component.RecordDivider
import io.issac.pleosandroid.feature.records.component.RecordHeading
import io.issac.pleosandroid.feature.records.component.RecordsPage
import io.issac.pleosandroid.feature.records.history.RecordsState
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun journeyPointsLabel(journey: JourneyRecord, points: RecordPoints): String = when (points) {
    RecordPoints.Loading -> "포인트 확인 중"

    RecordPoints.Unavailable -> "포인트 확인 필요"

    is RecordPoints.Ready -> points.wallet.journeyCredit(journey.id)?.deltaHalfPoints
        ?.takeIf { it > 0 }?.let { "+${it.pointLabel()}" } ?: "기록만 저장"
}

@Composable
internal fun JourneyPointsCard(journey: JourneyRecord, points: RecordPoints, onRetry: () -> Unit) {
    QuietPlate(contentPadding = 20.dp) {
        RecordHeading("포인트 적립 내역")
        when {
            points is RecordPoints.Loading -> RecordBody("포인트를 확인하고 있어요.")

            points is RecordPoints.Unavailable -> {
                RecordBody("포인트를 불러오지 못했어요.")
                PleosButton("다시 시도", onRetry)
            }

            points is RecordPoints.Ready -> {
                val credit = points.wallet.journeyCredit(journey.id)
                if (credit == null) {
                    RecordCaption("기록만 저장", accent = true)
                    RecordBody("이 여정에 적립된 포인트가 없어요.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp * PleosTextStyles.scale)) {
                        PointsLine("기본 포인트", "${(credit.basePointsHundredths / 100.0).decimalLabel(2)} P")
                        RecordCaption(
                            "인정 거리 ${credit.eligibleDistanceMeters.kilometerLabel()} × ${PointEarningPolicy.POINTS_PER_KILOMETER} P/km",
                        )
                        credit.multiplierThousandths?.let { multiplier ->
                            val title = credit.assessedScore?.let { "점수 반영 · ${it}점" } ?: "점수 반영"
                            PointsLine(title, "× ${(multiplier / 1_000.0).decimalLabel(3)}")
                        }
                        if (credit.membershipBonusHalfPoints > 0) {
                            PointsLine(
                                "주행 적립",
                                (credit.deltaHalfPoints - credit.membershipBonusHalfPoints).pointLabel(),
                            )
                            PointsLine("PLUS 추가 적립", "+${credit.membershipBonusHalfPoints.pointLabel()}")
                        }
                        RecordDivider()
                        PointsLine("총 적립", credit.deltaHalfPoints.pointLabel())
                    }
                }
            }
        }
    }
}

@Composable
private fun PointsLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp * PleosTextStyles.scale),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecordCaption(label, Modifier.weight(1f))
        RecordCaption(value, accent = true)
    }
}

@Composable
internal fun DrivingPointsWeekScreen(
    state: RecordsState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = Instant.now(),
) {
    RecordsPage("이번 주 포인트", modifier, onBack = onBack) { wide ->
        when {
            state.loading -> RecordBody("기록을 불러오고 있어요.")

            state.error -> {
                RecordBody("기록을 불러오지 못했어요.")
                PleosButton("다시 시도", onRetry)
            }

            else -> {
                val wallet = (state.points as? RecordPoints.Ready)?.wallet
                val summary = weeklyPoints(state.journeys, wallet ?: PointWallet(), now)
                val limit = PointEarningPolicy.WEEKLY_DISTANCE_LIMIT_METERS
                val colors = PleosTheme.colors
                RecordColumns(
                    wide,
                    leading = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp * PleosTextStyles.scale)) {
                            RecordCaption(
                                "${summary.start.format(
                                    WeekDateFormat,
                                )} – ${summary.start.plusDays(6).format(WeekDateFormat)}",
                                accent = true,
                            )
                            RecordHeading("이번 주의 주행 포인트")
                            when {
                                wallet != null -> RecordCaption("이번 주 받은 포인트 ${summary.earnedPoints.pointLabel()}")

                                state.points is RecordPoints.Loading -> RecordCaption("포인트를 확인하고 있어요.")

                                else -> {
                                    RecordCaption("포인트를 불러오지 못했어요.")
                                    PleosButton("다시 시도", onRetry)
                                }
                            }
                        }
                    },
                    trailing = {
                        QuietPlate {
                            ObservationRow("전체 기록된 거리", "이번 주 저장된 여정의 거리예요.", summary.totalMeters.kilometerLabel())
                            val accepted = when {
                                wallet != null -> summary.acceptedMeters.kilometerLabel()
                                state.points is RecordPoints.Loading -> "확인 중"
                                else -> "확인 필요"
                            }
                            ObservationRow("포인트 인정 거리", "주간 한도 ${limit.kilometerLabel()}", accepted)
                            if (wallet != null) {
                                LinearProgressIndicator(
                                    progress = { (summary.acceptedMeters.toFloat() / limit).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = colors.actionPrimary,
                                    trackColor = colors.borderDefault,
                                )
                            }
                            RecordCaption("거리 한도 이후에도 주행은 계속 기록돼요.")
                        }
                    },
                )
            }
        }
    }
}

private val WeekDateFormat = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
