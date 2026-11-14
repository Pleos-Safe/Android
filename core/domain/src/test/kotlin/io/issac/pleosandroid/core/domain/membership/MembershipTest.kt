package io.issac.pleosandroid.core.domain.membership

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class MembershipTest {
    @Test fun `monthly renewals preserve the original local day after February and skipped months`() {
        val start = Instant.parse("2028-01-30T15:30:00Z") // January 31 at 00:30 in Korea.
        val term = MembershipTerm(start)
        val feb = Instant.parse("2028-02-28T15:30:00Z")
        assertEquals(feb, term.nextRenewal(start))
        assertEquals(feb, term.nextRenewal(feb.minusNanos(1)))
        assertEquals(Instant.parse("2028-03-30T15:30:00Z"), term.nextRenewal(feb))
        assertEquals(Instant.parse("2029-02-27T15:30:00Z"), term.nextRenewal(start.plusSeconds(367L * 86400)))
        val ending = term.copy(endsAt = feb)
        assertEquals(MembershipStatus.Ending, Membership.at("id", ending, feb.minusNanos(1)).status)
        assertEquals(MembershipStatus.Expired, Membership.at("id", ending, feb).status)
    }

    @Test fun `splits actual observed distance at purchase and expiry boundaries`() {
        val start = Instant.EPOCH.plusSeconds(10)
        val end = start.plusSeconds(3)
        val term = MembershipTerm(start, end)
        assertEquals(0.0, term.coveredMeters(start, 2_000_000_000, 40.0), 0.0)
        assertEquals(20.0, term.coveredMeters(start.plusSeconds(1), 2_000_000_000, 40.0), 0.0)
        assertEquals(40.0, term.coveredMeters(end, 2_000_000_000, 40.0), 0.0)
        assertEquals(20.0, term.coveredMeters(end.plusSeconds(1), 2_000_000_000, 40.0), 0.0)
        assertEquals(0.0, term.coveredMeters(end.plusSeconds(2), 2_000_000_000, 40.0), 0.0)
    }
}
