package io.issac.pleosandroid.core.ui.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface UiState
interface UiIntent
interface UiEffect

/** Effects are foreground UI actions. Durable rewards and acknowledgements belong in the repository. */
abstract class BaseViewModel<S : UiState, I : UiIntent, E : UiEffect>(initialState: S) : ViewModel() {
    private val mutableState = MutableStateFlow(initialState)
    val state = mutableState.asStateFlow()
    private val effects = Channel<E>(Channel.BUFFERED)
    val effect = effects.receiveAsFlow()

    fun handleIntent(intent: I) = processIntent(intent)
    protected abstract fun processIntent(intent: I)
    protected fun updateState(reduce: S.() -> S) = mutableState.update(reduce)
    protected fun sendEffect(effect: E) {
        viewModelScope.launch { effects.send(effect) }
    }
}
