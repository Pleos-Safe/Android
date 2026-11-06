package io.issac.pleosandroid.feature.badges.reveal

import io.issac.pleosandroid.core.ui.mvi.UiIntent
import io.issac.pleosandroid.core.ui.mvi.UiState
import io.issac.pleosandroid.feature.badges.model.CollectionCard

internal data class CardRevealUiState(
    val loading: Boolean = true,
    val card: CollectionCard? = null,
    val error: String? = null,
    val saving: Boolean = false,
    val failedAttempt: Int = 0,
) : UiState

internal sealed interface CardRevealIntent : UiIntent {
    data class Load(val cardId: String) : CardRevealIntent
    data class AnimationCompleted(val level: Int) : CardRevealIntent
    data object Back : CardRevealIntent
    data object Later : CardRevealIntent
    data object ViewCollection : CardRevealIntent
    data object SetRepresentative : CardRevealIntent
}
