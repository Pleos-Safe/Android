package io.issac.pleosandroid.feature.badges.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.collection.repository.CollectionRepository
import io.issac.pleosandroid.feature.badges.model.CardUiMapper
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Read-only home projection; no cross-feature ViewModel is shared. */
@HiltViewModel
internal class CollectionHomeViewModel @Inject constructor(repository: CollectionRepository, mapper: CardUiMapper) :
    ViewModel() {
    val card = repository.observeRepresentativeCard().map { it?.let(mapper::card) }.catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
