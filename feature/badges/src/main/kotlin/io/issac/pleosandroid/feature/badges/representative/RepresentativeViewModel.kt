package io.issac.pleosandroid.feature.badges.representative

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.collection.model.MutationResult
import io.issac.pleosandroid.core.domain.collection.repository.CollectionRepository
import io.issac.pleosandroid.core.domain.collection.usecase.SetRepresentativeCardUseCase
import io.issac.pleosandroid.core.ui.mvi.BaseViewModel
import io.issac.pleosandroid.core.ui.mvi.UiIntent
import io.issac.pleosandroid.core.ui.mvi.UiState
import io.issac.pleosandroid.feature.badges.model.CardUiMapper
import io.issac.pleosandroid.feature.badges.model.CollectionCard
import io.issac.pleosandroid.feature.badges.navigation.CollectionEffect
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

internal data class RepresentativeUiState(
    val loading: Boolean = true,
    val cards: List<CollectionCard> = emptyList(),
    val currentId: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
) : UiState
internal sealed interface RepresentativeIntent : UiIntent {
    data object Retry : RepresentativeIntent
    data class Confirm(val cardId: String?) : RepresentativeIntent
    data object Back : RepresentativeIntent
}

@HiltViewModel
internal class RepresentativeViewModel @Inject constructor(
    private val repository: CollectionRepository,
    private val setRepresentative: SetRepresentativeCardUseCase,
    private val mapper: CardUiMapper,
) : BaseViewModel<RepresentativeUiState, RepresentativeIntent, CollectionEffect>(RepresentativeUiState()) {
    private var observation: Job? = null
    init {
        observe()
    }
    override fun processIntent(intent: RepresentativeIntent) {
        when (intent) {
            RepresentativeIntent.Retry -> observe()

            RepresentativeIntent.Back -> if (!state.value.saving) sendEffect(CollectionEffect.Back)

            is RepresentativeIntent.Confirm -> {
                if (state.value.saving) return
                updateState { copy(saving = true, error = null) }
                viewModelScope.launch {
                    try {
                        if (setRepresentative(intent.cardId) == MutationResult.Success) {
                            updateState { copy(saving = false) }
                            sendEffect(CollectionEffect.RepresentativeSaved)
                        } else {
                            updateState { copy(saving = false, error = "대표 카드를 설정할 수 없어요.") }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        updateState { copy(saving = false, error = "선택을 저장하지 못했어요. 다시 시도해 주세요.") }
                    }
                }
            }
        }
    }
    private fun observe() {
        observation?.cancel()
        observation = viewModelScope.launch {
            updateState { copy(loading = true, error = null) }
            repository.observeCollection().catch { updateState { copy(loading = false, error = "카드를 불러오지 못했어요.") } }
                .collect { snapshot ->
                    updateState {
                        copy(
                            loading = false,
                            cards = snapshot.cards.map(mapper::card),
                            currentId = snapshot.representativeId,
                        )
                    }
                }
        }
    }
}
