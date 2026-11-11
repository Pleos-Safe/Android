package io.issac.pleosandroid.core.ui.flow

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

/** Handle recoverable UI loading failures while preserving cancellation and fatal errors. */
fun <T> Flow<T>.catchUiError(action: suspend FlowCollector<T>.(Exception) -> Unit): Flow<T> = catch { error ->
    if (error is CancellationException || error !is Exception) throw error
    action(error)
}

suspend fun <T> Flow<T>.collectUiState(
    onLoading: suspend () -> Unit = {},
    onError: suspend (Exception) -> Unit,
    onState: suspend (T) -> Unit,
) {
    onStart { onLoading() }
        .onEach { onState(it) }
        .catchUiError { onError(it) }
        .collect()
}
