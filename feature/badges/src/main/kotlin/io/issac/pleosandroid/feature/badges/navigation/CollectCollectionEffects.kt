package io.issac.pleosandroid.feature.badges.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.BadgesRoute
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.core.navigation.MainRoute
import kotlinx.coroutines.flow.Flow

@Composable
internal fun CollectCollectionEffects(effects: Flow<CollectionEffect>, route: AppRoute) {
    val router by rememberUpdatedState(LocalNavigationRouter.current)
    val navigation = collectionNavigationViewModel()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(effects, route, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            effects.collect { effect ->
                if (!router.isActive(route) || !navigation.vehicle.value.allowsTabNavigation) return@collect
                when (effect) {
                    CollectionEffect.Back -> router.pop()

                    is CollectionEffect.Navigate -> router.navigate(effect.route)

                    is CollectionEffect.ShowOnShelf -> {
                        navigation.focusCard(effect.cardId)
                        if (router.currentBackStack.first() == BadgesRoute) {
                            router.popTo({ it == BadgesRoute })
                        } else {
                            router.pop()
                            router.resetTo(BadgesRoute)
                        }
                    }

                    CollectionEffect.RepresentativeSaved -> {
                        router.popTo({ it == BadgesRoute })
                        router.navigate(MainRoute)
                    }
                }
            }
        }
    }
}
