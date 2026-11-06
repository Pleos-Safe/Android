package io.issac.pleosandroid.feature.badges.detail

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.collection.repository.CollectionRepository
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.ui.mvi.BaseViewModel
import io.issac.pleosandroid.core.ui.mvi.UiIntent
import io.issac.pleosandroid.core.ui.mvi.UiState
import io.issac.pleosandroid.feature.badges.model.CardUiMapper
import io.issac.pleosandroid.feature.badges.model.CollectionCard
import io.issac.pleosandroid.feature.badges.navigation.CollectionEffect
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

internal data class CardDetailUiState(
    val loading: Boolean = true,
    val card: CollectionCard? = null,
    val representative: Boolean = false,
    val error: String? = null,
) : UiState

internal sealed interface CardDetailIntent : UiIntent {
    data class Load(val id: String) : CardDetailIntent
    data class Open(val route: AppRoute) : CardDetailIntent
    data object Back : CardDetailIntent
}

@HiltViewModel
internal class CardDetailViewModel @Inject constructor(
    private val repository: CollectionRepository,
    private val vehicle: VehicleRepository,
    private val mapper: CardUiMapper,
) : BaseViewModel<CardDetailUiState, CardDetailIntent, CollectionEffect>(CardDetailUiState()) {
    private var observation: Job? = null
    override fun processIntent(intent: CardDetailIntent) {
        when (intent) {
            is CardDetailIntent.Load -> {
                observation?.cancel()
                observation = viewModelScope.launch {
                    updateState { copy(loading = true, error = null) }
                    repository.observeCollection().catch {
                        updateState { copy(loading = false, error = "카드를 불러오지 못했어요.") }
                    }.collect { snapshot ->
                        updateState {
                            copy(
                                loading = false,
                                card = snapshot.cards.find { it.id == intent.id }?.let(mapper::card),
                                representative = snapshot.representativeId == intent.id,
                            )
                        }
                    }
                }
            }

            is CardDetailIntent.Open -> if (vehicle.state.value.allowsTabNavigation) {
                sendEffect(
                    CollectionEffect.Navigate(intent.route),
                )
            }

            CardDetailIntent.Back -> if (vehicle.state.value.allowsTabNavigation) sendEffect(CollectionEffect.Back)
        }
    }
}
