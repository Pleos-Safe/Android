package io.issac.pleosandroid.core.vehicle.datasource

import io.issac.pleosandroid.core.vehicle.datasource.connection.VehicleServiceException
import io.issac.pleosandroid.core.vehicle.datasource.connection.carSdkCall
import io.issac.pleosandroid.core.vehicle.datasource.connection.recoverVehicleService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleServiceRecoveryTest {
    @Test
    fun recoversAfterMoreThanThreeFailures() = runTest {
        var attempts = 0
        val values = mutableListOf<String>()
        val job = backgroundScope.launch {
            flow {
                if (++attempts <= 4) throw VehicleServiceException("disconnected")
                emit("healthy")
                awaitCancellation()
            }.recoverVehicleService(isHealthy = { it == "healthy" }, failureState = { "error" })
                .collect(values::add)
        }
        runCurrent()
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(5, attempts)
        assertEquals(listOf("error", "error", "error", "error", "healthy"), values)
        job.cancel()
    }

    @Test
    fun backoffIsCappedAndCancellationStopsPendingReconnect() = runTest {
        val attemptTimes = mutableListOf<Long>()
        val job = backgroundScope.launch {
            flow<String> {
                attemptTimes += testScheduler.currentTime
                throw VehicleServiceException("disconnected")
            }.recoverVehicleService(isHealthy = { false }, failureState = { "error" }).collect()
        }
        runCurrent()
        advanceTimeBy(91_000)
        runCurrent()
        assertEquals(
            listOf(0L, 1_000L, 3_000L, 7_000L, 15_000L, 31_000L, 61_000L, 91_000L),
            attemptTimes,
        )
        job.cancel()
        runCurrent()
        advanceTimeBy(90_000)
        runCurrent()
        assertEquals(8, attemptTimes.size)
    }

    @Test
    fun healthyReadResetsBackoff() = runTest {
        var attempts = 0
        val attemptTimes = mutableListOf<Long>()
        val job = backgroundScope.launch {
            flow {
                attemptTimes += testScheduler.currentTime
                if (++attempts == 2) emit("healthy")
                throw VehicleServiceException("disconnected")
            }.recoverVehicleService(isHealthy = {
                it == "healthy"
            }, failureState = { "error" }).collect()
        }
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf(0L, 1_000L, 2_000L), attemptTimes)
        job.cancel()
    }

    @Test
    fun permissionFailuresAreReportedWithoutAutomaticRetry() = runTest {
        var attempts = 0
        val values = mutableListOf<String>()
        flow<String> {
            attempts++
            throw VehicleServiceException("permission", permissionRequired = true)
        }.recoverVehicleService(isHealthy = {
            false
        }, failureState = { "permission" }).collect(values::add)
        assertEquals(1, attempts)
        assertEquals(listOf("permission"), values)
    }

    @Test
    fun cancellationAndProgrammingErrorsAreNeverConvertedIntoServiceFailures() = runTest {
        val cancellation = CancellationException("cancelled")
        try {
            carSdkCall("sdk") { throw cancellation }
            throw AssertionError("Expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        val programmingError = NullPointerException("programming error")
        try {
            flow<String> { throw programmingError }
                .recoverVehicleService(isHealthy = { false }, failureState = { "wrong" }).collect()
            throw AssertionError("Expected programming error")
        } catch (actual: NullPointerException) {
            assertSame(programmingError, actual)
        }
    }
}
