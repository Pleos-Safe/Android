package io.issac.pleosandroid.core.domain.collection.usecase

import io.issac.pleosandroid.core.domain.collection.model.BeginRevealResult
import io.issac.pleosandroid.core.domain.collection.model.MutationResult
import io.issac.pleosandroid.core.domain.collection.model.RevealToken
import io.issac.pleosandroid.core.domain.collection.repository.CollectionRepository
import io.issac.pleosandroid.core.domain.repository.VehicleRepository

class BeginCardRevealUseCase(private val cards: CollectionRepository, private val vehicle: VehicleRepository) {
    suspend operator fun invoke(cardId: String): BeginRevealResult =
        if (vehicle.state.value.allowsTabNavigation) cards.beginReveal(cardId) else BeginRevealResult.Restricted
}

class CompleteCardRevealUseCase(private val cards: CollectionRepository, private val vehicle: VehicleRepository) {
    suspend operator fun invoke(token: RevealToken): MutationResult =
        if (vehicle.state.value.allowsTabNavigation) cards.completeReveal(token) else MutationResult.Restricted
}

class SetRepresentativeCardUseCase(private val cards: CollectionRepository, private val vehicle: VehicleRepository) {
    suspend operator fun invoke(cardId: String?): MutationResult =
        if (vehicle.state.value.allowsTabNavigation) cards.setRepresentativeCard(cardId) else MutationResult.Restricted
}
