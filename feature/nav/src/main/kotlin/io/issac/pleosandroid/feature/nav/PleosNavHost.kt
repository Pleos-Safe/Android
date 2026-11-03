package io.issac.pleosandroid.feature.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.core.navigation.NavEntryResult
import io.issac.pleosandroid.core.navigation.NavigationRouter

@Composable
fun PleosNavHost(
    factories: Set<NavEntryFactory>,
    startRoute: AppRoute,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val backStack = rememberNavBackStack(startRoute)
    val router = remember(backStack) { NavigationRouter(backStack) }
    val currentFactories = rememberUpdatedState(factories)
    val currentPadding = rememberUpdatedState(contentPadding)

    CompositionLocalProvider(LocalNavigationRouter provides router) {
        NavDisplay(
            backStack = backStack,
            modifier = modifier,
            onBack = { router.pop() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = { key ->
                val route = key as? AppRoute ?: error("Unsupported navigation key: $key")
                val result = resolveNavEntryResult(route, currentFactories.value)
                NavEntry(key = key, metadata = result.metadata) {
                    // NavEntry content can outlive its provider's closure; read current screen data.
                    val currentResult = resolveNavEntryResult(route, currentFactories.value)
                    currentResult.content(currentPadding.value)
                }
            },
        )
    }
}

internal fun resolveNavEntryResult(route: AppRoute, factories: Set<NavEntryFactory>): NavEntryResult {
    val matches = factories.mapNotNull { it.create(route) }
    return when (matches.size) {
        0 -> error("No NavEntryFactory found for ${route::class.qualifiedName}")
        1 -> matches.single()
        else -> error("Multiple NavEntryFactory instances matched ${route::class.qualifiedName}")
    }
}
