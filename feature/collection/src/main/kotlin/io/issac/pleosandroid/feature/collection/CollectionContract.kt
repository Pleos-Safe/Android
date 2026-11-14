package io.issac.pleosandroid.feature.collection

import io.issac.pleosandroid.core.domain.cards.AlbumSnapshot
import io.issac.pleosandroid.core.ui.mvi.UiIntent
import io.issac.pleosandroid.core.ui.mvi.UiState

internal data class CollectionState(
    val album: AlbumSnapshot? = null,
    val loadFailed: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
) : UiState

internal sealed interface CollectionIntent : UiIntent {
    data class Open(val id: String) : CollectionIntent
    data class Reveal(val id: String) : CollectionIntent
    data class Feature(val id: String) : CollectionIntent
    data object Retry : CollectionIntent
    data object Dismiss : CollectionIntent
}
