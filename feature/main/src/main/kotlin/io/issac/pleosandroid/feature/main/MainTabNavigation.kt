package io.issac.pleosandroid.feature.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import io.issac.pleosandroid.core.designsystem.component.PleosTab
import io.issac.pleosandroid.core.navigation.BadgesRoute
import io.issac.pleosandroid.core.navigation.EnhancementRoute
import io.issac.pleosandroid.core.navigation.ExploreRoute
import io.issac.pleosandroid.core.navigation.MainRoute
import io.issac.pleosandroid.core.navigation.MainTabRoute
import io.issac.pleosandroid.core.navigation.RecordsRoute
import io.issac.pleosandroid.core.navigation.ShopRoute

internal val PleosTab.route: MainTabRoute
    get() = when (this) {
        PleosTab.Travel -> MainRoute
        PleosTab.Explore -> ExploreRoute
        PleosTab.Collection -> BadgesRoute
        PleosTab.Enhance -> EnhancementRoute
        PleosTab.Records -> RecordsRoute
        PleosTab.Shop -> ShopRoute
    }

@Stable
internal class MainTabNavigation(
    private val selection: MutableState<PleosTab>,
    private val stacks: Map<PleosTab, NavBackStack<NavKey>>,
) {
    val selectedTab: PleosTab get() = selection.value

    fun stackFor(tab: PleosTab): NavBackStack<NavKey> = stacks.getValue(tab)

    fun select(tab: PleosTab): Boolean {
        if (tab != selectedTab) {
            selection.value = tab
            return true
        }
        return popToRoot(tab)
    }

    fun returnToTravelHome() {
        selection.value = PleosTab.Travel
        popToRoot(PleosTab.Travel)
    }

    private fun popToRoot(tab: PleosTab): Boolean {
        val stack = stackFor(tab)
        if (stack.size == 1) return false
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        return true
    }

    fun select(route: MainTabRoute): NavBackStack<NavKey> {
        val tab = PleosTab.entries.first { it.route == route }
        select(tab)
        return stackFor(tab)
    }
}

@Composable
internal fun rememberMainTabNavigation(): MainTabNavigation {
    val selected = rememberSaveable { mutableStateOf(PleosTab.Travel) }
    val stacks = PleosTab.entries.associateWith { tab ->
        // Older saved stacks can contain retired level-card routes with no serializer.
        key(tab, "pack-album") { rememberNavBackStack(tab.route) }
    }
    return remember {
        selected.value = migrateShopStack(selected.value, stacks)
        MainTabNavigation(selected, stacks)
    }
}

internal fun migrateShopStack(selected: PleosTab, stacks: Map<PleosTab, NavBackStack<NavKey>>): PleosTab {
    var restored = selected
    val shop = stacks.getValue(PleosTab.Shop)
    for ((tab, stack) in stacks) {
        val index = stack.indexOf(ShopRoute)
        if (index <= 0) continue
        // Before the shop had its own tab, a purchase or management screen could be nested under home or settings.
        if (tab == selected || (restored != PleosTab.Shop && shop.size == 1)) {
            val children = stack.drop(index + 1)
            while (shop.size > 1) shop.removeAt(shop.lastIndex)
            shop.addAll(children)
        }
        while (stack.size > index) stack.removeAt(stack.lastIndex)
        if (tab == selected) restored = PleosTab.Shop
    }
    return restored
}
