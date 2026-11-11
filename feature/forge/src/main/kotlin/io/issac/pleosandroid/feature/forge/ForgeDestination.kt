package io.issac.pleosandroid.feature.forge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.issac.pleosandroid.core.cardui.component.CardAlbumContent
import io.issac.pleosandroid.core.cardui.component.CardDemoControl
import io.issac.pleosandroid.core.cardui.component.CollectionStatusScreen
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.BadgesRoute
import io.issac.pleosandroid.core.navigation.EnhancementRoute
import io.issac.pleosandroid.core.navigation.LocalCollectionFocus
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.core.navigation.MotionGalleryRoute
import io.issac.pleosandroid.core.navigation.PackShelfRoute
import io.issac.pleosandroid.core.navigation.WorkshopRoute

@Composable
internal fun ForgeDestination(route: AppRoute, modifier: Modifier, viewModel: ForgeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val router = LocalNavigationRouter.current
    val focus = LocalCollectionFocus.current
    val back: () -> Unit = { router.pop() }
    CardAlbumContent(
        album = state.album,
        loadFailed = state.loadFailed,
        message = state.message,
        onRetry = { viewModel.handleIntent(ForgeIntent.Retry) },
        onDismiss = { viewModel.handleIntent(ForgeIntent.Dismiss) },
        modifier = modifier,
    ) { album ->
        val send: (ForgeIntent) -> Unit = {
            if (router.isActive(route) && viewModel.state.value.album?.generation == album.generation) {
                viewModel.handleIntent(it)
            }
        }
        when (route) {
            EnhancementRoute -> ForgeSelectionScreen(
                album = album,
                onCard = { router.navigate(WorkshopRoute(it.id)) },
                openGallery = { router.navigate(MotionGalleryRoute(startOnForge = true)) },
                openInbox = { router.replaceAll(BadgesRoute, PackShelfRoute) },
                modeControl = {
                    if (viewModel.demoAvailable) {
                        CardDemoControl(album.demo, state.busy) {
                            send(ForgeIntent.SetDemo(it))
                        }
                    }
                },
                modifier = modifier,
            )

            is WorkshopRoute -> {
                val card = album.collection.find { it.id == route.copyId }
                if (card == null) {
                    CollectionStatusScreen("요청한 카드를 찾을 수 없어요.", modifier, onBack = back)
                } else {
                    key(card.id) {
                        CardWorkshop(
                            card,
                            album,
                            state.busy,
                            send,
                            back,
                            inCollection = { focus.showCard(router, route, it) },
                            modifier = modifier,
                        )
                    }
                }
            }

            else -> error("Unsupported forge route $route")
        }
    }
}
