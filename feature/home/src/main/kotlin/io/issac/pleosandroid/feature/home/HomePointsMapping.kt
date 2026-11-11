package io.issac.pleosandroid.feature.home

import io.issac.pleosandroid.core.domain.points.PointEarningPolicy
import io.issac.pleosandroid.core.domain.points.PointTransaction
import io.issac.pleosandroid.core.domain.points.PointTransactionKind
import io.issac.pleosandroid.core.domain.points.PointWallet

internal fun JourneySummary.withPoints(wallet: PointWallet?): JourneySummary {
    val credit = wallet?.transactions?.firstOrNull {
        it.kind == PointTransactionKind.JourneyEarned && it.sourceId == reportId
    }
    val week = endedAt?.let { PointEarningPolicy.weekStart(PointEarningPolicy.drivingDate(it)) }
    val recognizedThisWeek = wallet?.transactions.orEmpty().sumOf {
        if (week != null &&
            it.drivingDate?.let(PointEarningPolicy::weekStart) == week
        ) {
            it.eligibleDistanceMeters
        } else {
            0L
        }
    }
    return copy(
        earnedPoints = if (reportId != null && wallet != null) credit?.deltaHalfPoints ?: 0 else null,
        recognizedDistanceMeters = credit?.eligibleDistanceMeters,
        membershipBonusHalfPoints = credit?.membershipBonusHalfPoints ?: 0,
        settlementReason = pointSettlementReason(wallet, credit, recognizedThisWeek),
    )
}

private fun JourneySummary.pointSettlementReason(
    wallet: PointWallet?,
    credit: PointTransaction?,
    recognizedThisWeek: Long,
): String? = when {
    credit != null && credit.eligibleDistanceMeters < (distanceMeters ?: 0) ->
        "이번 주 한도까지 남은 거리만 포인트에 반영했어요."

    credit != null || reportId == null || wallet == null -> null

    (distanceMeters ?: 0) < PointEarningPolicy.MINIMUM_DISTANCE_METERS -> "2km 이상 달리면 포인트를 받아요."

    safetyScore == null -> "안전점수 자료가 부족해 이번 정산은 기록만 남겼어요."

    recognizedThisWeek >= PointEarningPolicy.WEEKLY_DISTANCE_LIMIT_METERS -> "이번 주 포인트 인정 거리를 모두 사용했어요."

    else -> "이번 주행의 포인트 인정 조건을 확인해 주세요."
}
