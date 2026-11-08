package io.issac.pleosandroid.feature.badges.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.issac.pleosandroid.core.domain.cards.CardAlbumRepository
import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.domain.cards.ReceivedPack
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal data class HomeAlbum(
    val featured: CardCopy? = null,
    val nextPack: ReceivedPack? = null,
    val inboxSize: Int = 0,
    val message: String? = null,
)

@HiltViewModel
internal class CollectionHomeViewModel @Inject constructor(repository: CardAlbumRepository) : ViewModel() {
    val album = repository.observe().map { album ->
        HomeAlbum(album.collection.find { it.id == album.featuredId }, album.inbox.firstOrNull(), album.inbox.size)
    }.distinctUntilChanged().catch { emit(HomeAlbum(message = "카드 보관함을 불러오지 못했어요.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeAlbum(message = "보관함을 확인하고 있어요."))
}
