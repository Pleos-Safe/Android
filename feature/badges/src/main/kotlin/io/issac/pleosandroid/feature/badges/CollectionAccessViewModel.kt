package io.issac.pleosandroid.feature.badges

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import javax.inject.Inject

@HiltViewModel
internal class CollectionAccessViewModel @Inject constructor(
    repository: VehicleRepository,
) : ViewModel() {
    val vehicle = repository.state
}
