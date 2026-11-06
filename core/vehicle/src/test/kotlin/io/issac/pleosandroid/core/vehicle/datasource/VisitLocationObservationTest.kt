package io.issac.pleosandroid.core.vehicle.datasource

import io.issac.pleosandroid.core.domain.collection.repository.VisitLocation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisitLocationObservationTest {
    private val fix = VisitLocation(35.15, 129.16, 10.0, 1_000_000_000)

    @Test
    fun grantingPermissionLaterStartsUpdatesAndRevokingItCancelsTheSubscription() = runTest {
        var allowed = false
        var subscriptions = 0
        var cancellations = 0
        val fixes = MutableSharedFlow<VisitLocation?>()
        val received = mutableListOf<VisitLocation?>()
        val job = backgroundScope.launch {
            observeVisitLocations(hasPermission = { allowed }, updates = {
                flow {
                    subscriptions++
                    try {
                        emitAll(fixes)
                    } finally {
                        cancellations++
                    }
                }
            }).collect(received::add)
        }
        runCurrent()
        assertEquals(listOf<VisitLocation?>(null), received)
        assertEquals(0, subscriptions)

        allowed = true
        advanceTimeBy(1_000)
        runCurrent()
        fixes.emit(fix)
        runCurrent()
        assertEquals(fix, received.last())
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, subscriptions)

        allowed = false
        advanceTimeBy(1_000)
        runCurrent()
        assertNull(received.last())
        assertEquals(1, cancellations)
        val count = received.size
        fixes.emit(fix.copy(elapsedNanos = 2_000_000_000))
        runCurrent()
        assertEquals(count, received.size)

        allowed = true
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, subscriptions)
        fixes.emit(fix)
        runCurrent()
        assertEquals(fix, received.last())
        job.cancel()
        runCurrent()
        assertEquals(2, cancellations)
    }

    @Test
    fun permissionPollingDoesNotKeepAStaleFixAlive() = runTest {
        val fixes = MutableSharedFlow<VisitLocation?>()
        val received = mutableListOf<VisitLocation?>()
        backgroundScope.launch {
            observeVisitLocations(hasPermission = { true }, updates = { fixes }).collect(received::add)
        }
        runCurrent()
        fixes.emit(fix)
        runCurrent()
        advanceTimeBy(14_999)
        runCurrent()
        assertEquals(listOf(fix), received)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(fix, null), received)

        val next = fix.copy(elapsedNanos = 16_000_000_000)
        fixes.emit(next)
        runCurrent()
        assertEquals(next, received.last())
    }
}
