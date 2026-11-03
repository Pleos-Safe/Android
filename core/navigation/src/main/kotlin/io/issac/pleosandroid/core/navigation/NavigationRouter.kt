package io.issac.pleosandroid.core.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/** UI-owned navigation commands. Keep at least one route and a unique key for each entry. */
@Stable
class NavigationRouter(private val backStack: NavBackStack<NavKey>) {
    init {
        require(backStack.isNotEmpty()) { "Navigation back stack cannot be empty" }
        require(backStack.all { it is AppRoute }) { "Every navigation key must implement AppRoute" }
        require(backStack.distinct().size == backStack.size) { "Navigation routes must be unique" }
    }

    val currentBackStack: List<NavKey>
        get() = backStack

    val currentEntry: AppRoute
        get() = backStack.last() as AppRoute

    val previousEntry: AppRoute?
        get() = backStack.getOrNull(backStack.lastIndex - 1) as? AppRoute

    val canPop: Boolean
        get() = backStack.size > 1

    fun navigate(route: AppRoute): Boolean {
        if (currentEntry == route) return false
        // Reuse an existing entry instead of creating duplicate NavEntry state owners.
        if (route in backStack) return popTo(routeMatcher = { it == route })
        backStack.add(route)
        return true
    }

    fun pop(): Boolean {
        if (!canPop) return false
        backStack.removeLastOrNull()
        return true
    }

    fun replace(route: AppRoute): Boolean {
        if (currentEntry == route) return false
        if (route in backStack) return popTo(routeMatcher = { it == route })
        backStack[backStack.lastIndex] = route
        return true
    }

    fun popTo(routeMatcher: (AppRoute) -> Boolean, inclusive: Boolean = false): Boolean {
        val targetIndex = backStack.indexOfLast { routeMatcher(it as AppRoute) }
        if (targetIndex == -1) return false
        val lastIndexToKeep = if (inclusive) targetIndex - 1 else targetIndex
        if (lastIndexToKeep < 0 || backStack.lastIndex == lastIndexToKeep) return false
        while (backStack.lastIndex > lastIndexToKeep) backStack.removeLastOrNull()
        return true
    }

    fun replaceAll(vararg routes: AppRoute) {
        require(routes.isNotEmpty()) { "Navigation back stack cannot be empty" }
        require(routes.distinct().size == routes.size) { "Navigation routes must be unique" }
        backStack.clear()
        backStack.addAll(routes)
    }

    fun resetTo(route: AppRoute) {
        replaceAll(route)
    }
}

val LocalNavigationRouter = staticCompositionLocalOf<NavigationRouter> {
    error("NavigationRouter is not available outside PleosNavHost")
}

inline fun <reified T : AppRoute> NavigationRouter.popTo(inclusive: Boolean = false): Boolean =
    popTo(routeMatcher = { it is T }, inclusive = inclusive)
