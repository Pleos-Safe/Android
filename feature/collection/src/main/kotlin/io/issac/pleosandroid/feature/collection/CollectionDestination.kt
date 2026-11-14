package io.issac.pleosandroid.feature.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.issac.pleosandroid.core.cardui.component.CardAlbumContent
import io.issac.pleosandroid.core.cardui.component.CollectionStatusScreen
import io.issac.pleosandroid.core.domain.cards.CopyStatus
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.BadgesRoute
import io.issac.pleosandroid.core.navigation.CardDetailRoute
import io.issac.pleosandroid.core.navigation.DrivingReportRoute
import io.issac.pleosandroid.core.navigation.EnhancementRoute
import io.issac.pleosandroid.core.navigation.LocalCollectionFocus
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.core.navigation.MotionGalleryRoute
import io.issac.pleosandroid.core.navigation.OpenPackRoute
import io.issac.pleosandroid.core.navigation.PackOddsRoute
import io.issac.pleosandroid.core.navigation.PackShelfRoute
import io.issac.pleosandroid.core.navigation.WorkshopRoute
import io.issac.pleosandroid.feature.collection.detail.CardDetails
import io.issac.pleosandroid.feature.collection.pack.PackOpening
import io.issac.pleosandroid.feature.collection.pack.PackShelf
import io.issac.pleosandroid.feature.collection.shelf.CollectionShelfScreen

@Composable
internal fun CollectionDestination(
    route: AppRoute,
    modifier: Modifier,
    viewModel: CollectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val router = LocalNavigationRouter.current
    val focus = LocalCollectionFocus.current
    val back: () -> Unit = { router.pop() }
    CardAlbumContent(
        album = state.album,
        loadFailed = state.loadFailed,
        message = state.message,
        onRetry = { viewModel.handleIntent(CollectionIntent.Retry) },
        onDismiss = { viewModel.handleIntent(CollectionIntent.Dismiss) },
        modifier = modifier,
    ) { album ->
        val send: (CollectionIntent) -> Unit = {
            if (router.isActive(route) && viewModel.state.value.album?.generation == album.generation) {
                viewModel.handleIntent(it)
            }
        }
        val inbox: () -> Unit = { router.replaceAll(BadgesRoute, PackShelfRoute) }
        when (route) {
            BadgesRoute -> CollectionShelfScreen(
                album = album,
                focusId = focus.copyId,
                onFocused = focus::consumed,
                onCard = { card ->
                    val next = if (card.status == CopyStatus.Trace || card.id in album.pending) {
                        WorkshopRoute(card.id)
                    } else {
                        CardDetailRoute(card.id)
                    }
                    router.navigate(next)
                },
                openInbox = inbox,
                openGallery = { router.navigate(MotionGalleryRoute()) },
                modifier = modifier,
            )

            PackShelfRoute -> PackShelf(
                album,
                back,
                open = { router.navigate(OpenPackRoute(it)) },
                chances = { router.navigate(PackOddsRoute) },
                modifier = modifier,
            )

            is OpenPackRoute -> {
                val pack = album.packs.find { it.id == route.packId }
                if (pack == null) {
                    CollectionStatusScreen("요청한 카드나 팩을 찾을 수 없어요.", modifier, onBack = back)
                } else {
                    key(pack.id) {
                        PackOpening(
                            pack,
                            album.byPack[pack.id],
                            state.busy,
                            send,
                            back,
                            inCollection = { focus.showCard(router, route, it) },
                            modifier = modifier,
                        )
                    }
                }
            }

            is CardDetailRoute -> {
                val card = album.collection.find { it.id == route.copyId }
                if (card == null) {
                    CollectionStatusScreen("요청한 카드나 팩을 찾을 수 없어요.", modifier, onBack = back)
                } else {
                    CardDetails(
                        card,
                        album,
                        state.busy,
                        featured = { send(CollectionIntent.Feature(card.id)) },
                        forge = { router.replaceAll(EnhancementRoute, WorkshopRoute(card.id)) },
                        openJourney = { router.navigate(DrivingReportRoute(it)) },
                        back = back,
                        modifier = modifier,
                    )
                }
            }

            else -> error("Unsupported collection route $route")
        }
    }
}
