package io.issac.pleosandroid.feature.badges.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import javax.inject.Inject

/** Activity-scoped only for cross-entry UI navigation; all card state comes from repository flows. */
@HiltViewModel
internal class CollectionNavigationViewModel @Inject constructor(
    vehicle: VehicleRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    val vehicle = vehicle.state
    val shelfTarget = saved.getStateFlow<String?>("collectionShelfTarget", null)
    fun focusCard(id: String) {
        saved["collectionShelfTarget"] = id
    }
    fun targetHandled(id: String) {
        if (shelfTarget.value == id) saved["collectionShelfTarget"] = null
    }
}
