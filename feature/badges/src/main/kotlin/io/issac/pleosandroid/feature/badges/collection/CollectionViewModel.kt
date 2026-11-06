package io.issac.pleosandroid.feature.badges.collection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.collection.repository.CollectionDemoRepository
import io.issac.pleosandroid.core.domain.collection.repository.CollectionRepository
import io.issac.pleosandroid.core.domain.collection.repository.PlaceRepository
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import io.issac.pleosandroid.core.ui.mvi.BaseViewModel
import io.issac.pleosandroid.feature.badges.model.CardKind
import io.issac.pleosandroid.feature.badges.model.CardUiMapper
import io.issac.pleosandroid.feature.badges.navigation.CollectionEffect
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@HiltViewModel
internal class CollectionViewModel @Inject constructor(
    private val cards: CollectionRepository,
    private val places: PlaceRepository,
    private val demo: CollectionDemoRepository,
    private val vehicle: VehicleRepository,
    private val mapper: CardUiMapper,
    private val saved: SavedStateHandle,
) : BaseViewModel<CollectionUiState, CollectionIntent, CollectionEffect>(CollectionUiState()) {
    private var observation: Job? = null

    init {
        val values = saved.get<ArrayList<String>>("collection_filter")
        updateState {
            copy(
                demoAvailable = demo.available,
                filter = if (values?.size == 4) {
                    CollectionFilter(
                        CardKind.entries.find { it.name == values[0] },
                        values[1].ifEmpty { null },
                        values[2].ifEmpty { null },
                        CardOrder.entries.find { it.name == values[3] } ?: CardOrder.FirstKept,
                    )
                } else {
                    CollectionFilter()
                },
            )
        }
        observe()
    }

    override fun processIntent(intent: CollectionIntent) {
        when (intent) {
            CollectionIntent.Retry -> observe()

            is CollectionIntent.FilterChanged -> {
                val f = intent.filter
                saved["collection_filter"] =
                    arrayListOf(f.kind?.name.orEmpty(), f.district.orEmpty(), f.placeType.orEmpty(), f.order.name)
                updateState { copy(filter = f) }
            }

            is CollectionIntent.Open -> if (vehicle.state.value.allowsTabNavigation) {
                sendEffect(
                    CollectionEffect.Navigate(intent.route),
                )
            }

            CollectionIntent.Back -> if (vehicle.state.value.allowsTabNavigation) sendEffect(CollectionEffect.Back)

            CollectionIntent.ToggleDemo -> mutateDemo { demo.setEnabled(!state.value.demoEnabled) }

            CollectionIntent.ResetDemo -> mutateDemo { demo.resetDemo() }
        }
    }

    private fun observe() {
        observation?.cancel()
        observation = viewModelScope.launch {
            updateState { copy(loading = true, error = null) }
            combine(cards.observeCollection(), places.observePlaces(), demo.enabled) {
                    collection,
                    places,
                    demoEnabled,
                ->
                Triple(collection, places.map { mapper.place(it.place) }, demoEnabled)
            }.catch { updateState { copy(loading = false, error = "카드를 불러오지 못했어요. 다시 시도해 주세요.") } }
                .collect { (collection, places, demoEnabled) ->
                    updateState {
                        copy(
                            loading = false,
                            cards = collection.cards.map(mapper::card),
                            representativeId = collection.representativeId,
                            places = places,
                            demoEnabled = demoEnabled,
                            error = null,
                        )
                    }
                }
        }
    }

    private fun mutateDemo(action: suspend () -> Unit) {
        if (state.value.busy || !demo.available || !vehicle.state.value.allowsTabNavigation) return
        updateState { copy(busy = true) }
        viewModelScope.launch {
            try {
                action()
                updateState { copy(busy = false, error = null) }
                observe()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateState { copy(busy = false, error = "데모 카드를 준비하지 못했어요.") }
            }
        }
    }
}
