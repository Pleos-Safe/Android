package io.issac.pleosandroid.feature.records.points

import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.points.PointEarningPolicy
import io.issac.pleosandroid.core.domain.points.PointRepository
import io.issac.pleosandroid.core.domain.points.PointTransaction
import io.issac.pleosandroid.core.domain.points.PointTransactionKind
import io.issac.pleosandroid.core.domain.points.PointWallet
import io.issac.pleosandroid.core.domain.points.formatHalfPoints
import io.issac.pleosandroid.core.ui.flow.catchUiError
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal sealed interface RecordPoints {
    data object Loading : RecordPoints
    data object Unavailable : RecordPoints
    data class Ready(val wallet: PointWallet) : RecordPoints
}

internal fun PointRepository.observeRecordPoints(demo: Boolean) = observeWallet()
    .filter { it.demo == demo }
    .map<PointWallet, RecordPoints> { RecordPoints.Ready(it) }
    .onStart { emit(RecordPoints.Loading) }
    .catchUiError { emit(RecordPoints.Unavailable) }

internal fun PointWallet.journeyCredit(id: String): PointTransaction? = transactions.firstOrNull {
    it.kind == PointTransactionKind.JourneyEarned && it.sourceId == id
}

internal data class WeeklyPoints(
    val start: LocalDate,
    val totalMeters: Long,
    val acceptedMeters: Long,
    val earnedPoints: Long,
)

internal fun weeklyPoints(journeys: List<JourneyRecord>, wallet: PointWallet, now: Instant): WeeklyPoints {
    val week = PointEarningPolicy.weekStart(PointEarningPolicy.drivingDate(now))
    val weekEnd = week.plusWeeks(1)
    val credits = wallet.transactions.filter {
        val date = it.drivingDate ?: PointEarningPolicy.drivingDate(it.occurredAt)
        it.kind == PointTransactionKind.JourneyEarned && date >= week && date < weekEnd
    }
    val recorded = journeys.asSequence().filter {
        val date = PointEarningPolicy.drivingDate(it.endedAt)
        date >= week && date < weekEnd
    }.sumOf { it.distanceMeters }
    return WeeklyPoints(
        week,
        recorded,
        credits.sumOf {
            it.eligibleDistanceMeters
        },
        credits.sumOf { it.deltaHalfPoints },
    )
}

internal fun Long.pointLabel() = formatHalfPoints(this)

internal fun Double.decimalLabel(maxDigits: Int) = NumberFormat.getNumberInstance(Locale.KOREAN).apply {
    maximumFractionDigits = maxDigits
}.format(this)

internal fun Long.kilometerLabel() = "${(this / 1_000.0).decimalLabel(3)} km"
