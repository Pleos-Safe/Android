package io.issac.pleosandroid.feature.badges.guide

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.collection.repository.PlaceRepository
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.ui.mvi.BaseViewModel
import io.issac.pleosandroid.core.ui.mvi.UiIntent
import io.issac.pleosandroid.core.ui.mvi.UiState
import io.issac.pleosandroid.feature.badges.model.CardUiMapper
import io.issac.pleosandroid.feature.badges.model.CollectionPlace
import io.issac.pleosandroid.feature.badges.navigation.CollectionEffect
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

internal data class CollectionGuideUiState(
    val loading: Boolean = true,
    val places: List<CollectionPlace> = emptyList(),
    val error: String? = null,
) : UiState
internal sealed interface CollectionGuideIntent : UiIntent {
    data object Retry : CollectionGuideIntent
    data object Back : CollectionGuideIntent
    data class Open(val route: AppRoute) : CollectionGuideIntent
}

@HiltViewModel
internal class CollectionGuideViewModel @Inject constructor(
    private val repository: PlaceRepository,
    private val mapper: CardUiMapper,
) : BaseViewModel<CollectionGuideUiState, CollectionGuideIntent, CollectionEffect>(CollectionGuideUiState()) {
    private var observation: Job? = null
    init {
        observe()
    }
    override fun processIntent(intent: CollectionGuideIntent) {
        when (intent) {
            CollectionGuideIntent.Retry -> observe()
            CollectionGuideIntent.Back -> sendEffect(CollectionEffect.Back)
            is CollectionGuideIntent.Open -> sendEffect(CollectionEffect.Navigate(intent.route))
        }
    }
    private fun observe() {
        observation?.cancel()
        observation = viewModelScope.launch {
            updateState { copy(loading = true, error = null) }
            repository.observePlaces().catch { updateState { copy(loading = false, error = "장소를 불러오지 못했어요.") } }
                .collect { places ->
                    updateState { copy(loading = false, places = places.map { mapper.place(it.place) }) }
                }
        }
    }
}
