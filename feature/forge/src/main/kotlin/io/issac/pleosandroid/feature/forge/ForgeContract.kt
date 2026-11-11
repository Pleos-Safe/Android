package io.issac.pleosandroid.feature.forge

import io.issac.pleosandroid.core.domain.cards.AlbumSnapshot
import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.ui.mvi.UiIntent
import io.issac.pleosandroid.core.ui.mvi.UiState

internal data class ForgeState(
    val album: AlbumSnapshot? = null,
    val loadFailed: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
) : UiState

internal sealed interface ForgeIntent : UiIntent {
    data class Enhance(val card: CardCopy, val requestId: String) : ForgeIntent
    data class Restore(val trace: CardCopy, val material: String, val requestId: String) : ForgeIntent
    data class Acknowledge(val id: String) : ForgeIntent
    data class SetDemo(val enabled: Boolean) : ForgeIntent
    data object Retry : ForgeIntent
    data object Dismiss : ForgeIntent
}
