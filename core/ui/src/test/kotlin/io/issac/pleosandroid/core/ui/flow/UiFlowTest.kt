package io.issac.pleosandroid.core.ui.flow

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UiFlowTest {
    @Test
    fun `loading and values precede a recoverable failure`() = runTest {
        val events = mutableListOf<String>()
        flow {
            emit(1)
            throw IOException("offline")
        }.collectUiState(
            onLoading = { events += "loading" },
            onError = { events += it.message.orEmpty() },
            onState = { events += "value $it" },
        )
        assertEquals(listOf("loading", "value 1", "offline"), events)
    }

    @Test
    fun `state update failures use the same error handler`() = runTest {
        val failure = IllegalStateException("state update")
        var handled: Exception? = null
        flowOf(1).collectUiState(onError = { handled = it }, onState = { throw failure })
        assertSame(failure, handled)
    }

    @Test
    fun `explicit cancellation is propagated without an error state`() = runTest {
        val cancellation = CancellationException("screen left")
        var handled = false
        var propagated: CancellationException? = null
        try {
            flow<Int> { throw cancellation }.collectUiState(
                onError = { handled = true },
                onState = {},
            )
        } catch (error: CancellationException) {
            propagated = error
        }
        assertSame(cancellation, propagated)
        assertEquals(false, handled)
    }

    @Test
    fun `fatal errors are propagated without an error state`() = runTest {
        val failure = AssertionError("fatal")
        var handled = false
        var propagated: Error? = null
        try {
            flow<Int> { throw failure }.collectUiState(onError = { handled = true }, onState = {})
        } catch (error: Error) {
            propagated = error
        }
        assertSame(failure, propagated)
        assertEquals(false, handled)
    }

    @Test
    fun `a new request cancels the previous subscription without reporting failure`() = runTest {
        val requests = MutableStateFlow(0)
        val cancelled = mutableListOf<Int>()
        val observed = mutableListOf<Int>()
        val failures = mutableListOf<Exception>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            requests.collectLatest { request ->
                flow {
                    try {
                        emit(request)
                        awaitCancellation()
                    } finally {
                        cancelled += request
                    }
                }.collectUiState(onError = { failures += it }, onState = { observed += it })
            }
        }
        requests.value = 1
        runCurrent()
        assertEquals(listOf(0, 1), observed)
        assertEquals(listOf(0), cancelled)
        assertEquals(emptyList<Exception>(), failures)
    }

    @Test
    fun `a failed subscription can be started again by the next request`() = runTest {
        val requests = MutableStateFlow(0)
        val observed = mutableListOf<Int>()
        var failures = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            requests.collectLatest { request ->
                flow {
                    if (request == 0) throw IOException("retry")
                    emit(request)
                }.collectUiState(onError = { failures += 1 }, onState = { observed += it })
            }
        }
        requests.value = 1
        runCurrent()
        assertEquals(1, failures)
        assertEquals(listOf(1), observed)
    }
}
