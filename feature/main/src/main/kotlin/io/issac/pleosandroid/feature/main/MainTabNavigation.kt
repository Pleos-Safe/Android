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
import io.issac.pleosandroid.core.navigation.MainRoute
import io.issac.pleosandroid.core.navigation.MainTabRoute
import io.issac.pleosandroid.core.navigation.RecordsRoute

internal val PleosTab.route: MainTabRoute
    get() = when (this) {
        PleosTab.Travel -> MainRoute
        PleosTab.Collection -> BadgesRoute
        PleosTab.Records -> RecordsRoute
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
        key(tab) { rememberNavBackStack(tab.route) }
    }
    return remember { MainTabNavigation(selected, stacks) }
}
