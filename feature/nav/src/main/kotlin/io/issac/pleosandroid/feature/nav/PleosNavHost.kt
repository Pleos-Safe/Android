package io.issac.pleosandroid.feature.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import io.issac.pleosandroid.core.designsystem.component.PleosTab
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
    PleosNavDisplay(
        entries = rememberPleosNavEntries(backStack, router, factories, contentPadding),
        modifier = modifier,
        onBack = { router.pop() },
    )
}

/** Keep this in composition for every tab so inactive entries retain their state and ViewModels. */
@Composable
fun rememberPleosNavEntries(
    backStack: List<NavKey>,
    router: NavigationRouter,
    factories: Set<NavEntryFactory>,
    contentPadding: PaddingValues = PaddingValues(),
    tab: PleosTab? = null,
): List<NavEntry<NavKey>> {
    val currentFactories = rememberUpdatedState(factories)
    val currentPadding = rememberUpdatedState(contentPadding)
    return rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = { key ->
            val route = key as? AppRoute ?: error("Unsupported navigation key: $key")
            val result = resolveNavEntryResult(route, currentFactories.value)
            val contentKey = if (tab == null) key.toString() else "${tab.name}/$key"
            val metadata = if (tab == null) result.metadata else result.metadata + (TAB_METADATA_KEY to tab)
            NavEntry(key = key, contentKey = contentKey, metadata = metadata) {
                CompositionLocalProvider(LocalNavigationRouter provides router) {
                    resolveNavEntryResult(route, currentFactories.value).content(currentPadding.value)
                }
            }
        },
    )
}

internal fun resolveNavEntryResult(route: AppRoute, factories: Set<NavEntryFactory>): NavEntryResult {
    val matches = factories.mapNotNull { it.create(route) }
    return when (matches.size) {
        0 -> error("No NavEntryFactory found for ${route::class.qualifiedName}")
        1 -> matches.single()
        else -> error("Multiple NavEntryFactory instances matched ${route::class.qualifiedName}")
    }
}
