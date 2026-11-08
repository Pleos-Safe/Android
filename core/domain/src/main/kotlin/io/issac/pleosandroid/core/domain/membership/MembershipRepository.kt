package io.issac.pleosandroid.core.domain.membership

import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow

/** Contiguous entitlement, including the already-paid period after renewal is cancelled. */
data class MembershipTerm(val startedAt: Instant, val endsAt: Instant? = null) {
    fun covers(start: Instant, end: Instant): Boolean = start >= startedAt && (endsAt == null || end <= endsAt)

    /** Split only the speed observation that actually crosses a purchase or expiry. */
    fun coveredMeters(end: Instant, durationNanos: Long, meters: Double): Double {
        val start = end.minusNanos(durationNanos)
        if (end <= startedAt || (endsAt != null && start >= endsAt)) return 0.0
        if (covers(start, end)) return meters
        val overlap = Duration.between(maxOf(start, startedAt), minOf(end, endsAt ?: end)).toNanos()
        return meters * overlap / durationNanos
    }

    fun nextRenewal(now: Instant): Instant {
        val anchor = startedAt.atZone(zone)
        val months = ChronoUnit.MONTHS.between(
            YearMonth.from(anchor),
            YearMonth.from(now.atZone(zone)),
        ).coerceAtLeast(0)
        val anniversary = anchor.plusMonths(months).toInstant()
        return if (anniversary > now) anniversary else anchor.plusMonths(months + 1).toInstant()
    }

    companion object {
        val zone: ZoneId = ZoneId.of("Asia/Seoul")
    }
}

enum class MembershipStatus { Inactive, Renewing, Ending, Expired }
enum class MembershipAction { Subscribe, CancelRenewal, ResumeRenewal }

data class Membership(
    val generation: String,
    val demo: Boolean,
    val term: MembershipTerm?,
    val status: MembershipStatus,
    /** Renewal, scheduled expiry, or the most recent expiry. */
    val periodEnd: Instant?,
    val revision: Long,
) {
    val hasBenefit get() = status == MembershipStatus.Renewing || status == MembershipStatus.Ending

    companion object {
        fun at(generation: String, demo: Boolean, term: MembershipTerm?, now: Instant, revision: Long = 0): Membership {
            val status = when {
                term == null || now < term.startedAt -> MembershipStatus.Inactive
                term.endsAt == null -> MembershipStatus.Renewing
                now < term.endsAt -> MembershipStatus.Ending
                else -> MembershipStatus.Expired
            }
            val end = if (status == MembershipStatus.Renewing) term?.nextRenewal(now) else term?.endsAt
            return Membership(generation, demo, term, status, end, revision)
        }
    }
}

sealed interface MembershipChange {
    data object Applied : MembershipChange
    data object Changed : MembershipChange
    data object Unavailable : MembershipChange
}

interface MembershipRepository {
    fun observeMembership(): Flow<Membership>

    /** Apply only to the profile and billing period the user confirmed. */
    suspend fun change(action: MembershipAction, expected: Membership): MembershipChange
}
