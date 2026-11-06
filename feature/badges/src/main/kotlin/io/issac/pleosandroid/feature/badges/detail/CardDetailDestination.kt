package io.issac.pleosandroid.feature.badges.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.CardDetailRoute
import io.issac.pleosandroid.core.navigation.DrivingReportRoute
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.feature.badges.component.Caption
import io.issac.pleosandroid.feature.badges.component.CollectionBack
import io.issac.pleosandroid.feature.badges.component.CollectionPage
import io.issac.pleosandroid.feature.badges.component.CollectionStatusScreen
import io.issac.pleosandroid.feature.badges.component.CollectionTitle
import io.issac.pleosandroid.feature.badges.component.SectionTitle
import io.issac.pleosandroid.feature.badges.model.displayDate
import io.issac.pleosandroid.feature.badges.navigation.CardMemoriesRoute
import io.issac.pleosandroid.feature.badges.navigation.CollectCollectionEffects
import io.issac.pleosandroid.feature.badges.navigation.CollectionMemoryRoute
import io.issac.pleosandroid.feature.badges.navigation.RevealCardRoute

@Composable
internal fun CardDetailDestination(
    route: AppRoute,
    modifier: Modifier,
    viewModel: CardDetailViewModel = hiltViewModel(),
) {
    val id = when (route) {
        is CardDetailRoute -> route.cardId
        is CardMemoriesRoute -> route.cardId
        is CollectionMemoryRoute -> route.cardId
        else -> error("Unknown card route")
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val router = LocalNavigationRouter.current
    CollectCollectionEffects(viewModel.effect, route)
    LaunchedEffect(id) { viewModel.handleIntent(CardDetailIntent.Load(id)) }
    val back = { viewModel.handleIntent(CardDetailIntent.Back) }
    val card = state.card
    if (state.loading || state.error != null || card == null) {
        CollectionStatusScreen(
            state.error ?: if (state.loading) "카드를 불러오고 있어요" else "이 카드를 찾을 수 없어요",
            modifier,
            back,
            if (state.error !=
                null
            ) {
                { viewModel.handleIntent(CardDetailIntent.Load(id)) }
            } else {
                null
            },
        )
        return
    }
    when (route) {
        is CardDetailRoute -> if (card.revealedLevel == null) {
            LaunchedEffect(id) { if (router.isActive(route)) router.replace(RevealCardRoute(id)) }
        } else {
            CardDetailScreen(card, state.representative, back, {
                viewModel.handleIntent(CardDetailIntent.Open(it))
            }, modifier)
        }

        is CardMemoriesRoute -> CardMemoriesScreen(
            card,
            back,
            { viewModel.handleIntent(CardDetailIntent.Open(CollectionMemoryRoute(id, it))) },
            modifier,
        )

        is CollectionMemoryRoute -> {
            val memory = card.memories.find { it.id == route.memoryId }
            CollectionPage(modifier) { window ->
                CollectionBack("카드의 기억", back)
                CollectionTitle(memory?.title ?: "이 기록을 찾을 수 없어요", window.full)
                if (memory !=
                    null
                ) {
                    Caption(memory.date.displayDate())
                    SectionTitle(card.publicTitle)
                    Caption(memory.description)
                    memory.journeyId?.let { journeyId ->
                        PleosTextLink("운전습관 보기", {
                            viewModel.handleIntent(CardDetailIntent.Open(DrivingReportRoute(journeyId)))
                        })
                    }
                }
            }
        }

        else -> Unit
    }
}
