package io.issac.pleosandroid.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRouterTest {
    @Test
    fun `root survives repeated back events`() {
        val router = router(HomeRoute)

        assertFalse(router.pop())
        assertFalse(router.pop())
        assertFalse(router.canPop)
        assertEquals(listOf(HomeRoute), router.currentBackStack)
    }

    @Test
    fun `consecutive navigation and back events preserve order`() {
        val router = router(HomeRoute)

        assertTrue(router.navigate(DetailRoute("first")))
        assertTrue(router.navigate(DetailRoute("second")))
        assertEquals(DetailRoute("first"), router.previousEntry)
        assertTrue(router.pop())
        assertEquals(DetailRoute("first"), router.currentEntry)
        assertTrue(router.pop())
        assertEquals(HomeRoute, router.currentEntry)
    }

    @Test
    fun `repeated tap on current destination does not add an entry`() {
        val router = router(HomeRoute, DetailRoute("first"))

        assertFalse(router.navigate(DetailRoute("first")))
        assertEquals(listOf(HomeRoute, DetailRoute("first")), router.currentBackStack)
    }

    @Test
    fun `returning to existing destination removes only entries above it`() {
        val router = router(HomeRoute, DetailRoute("first"), DetailRoute("second"), BadgesRoute)

        assertTrue(router.navigate(DetailRoute("first")))

        assertEquals(listOf(HomeRoute, DetailRoute("first")), router.currentBackStack)
        assertTrue(router.navigate(HomeRoute))
        assertEquals(listOf(HomeRoute), router.currentBackStack)
    }

    @Test
    fun `replace removes the outgoing destination from back history`() {
        val router = router(HomeRoute, DetailRoute("first"))

        assertTrue(router.replace(BadgesRoute))
        assertFalse(router.replace(BadgesRoute))
        assertEquals(listOf(HomeRoute, BadgesRoute), router.currentBackStack)
        assertTrue(router.pop())
        assertEquals(HomeRoute, router.currentEntry)
    }

    @Test
    fun `replace with an existing destination reuses its entry`() {
        val router = router(HomeRoute, DetailRoute("first"), BadgesRoute)

        assertTrue(router.replace(DetailRoute("first")))

        assertEquals(listOf(HomeRoute, DetailRoute("first")), router.currentBackStack)
    }

    @Test
    fun `reset discards the previous flow and preserves a root`() {
        val router = router(HomeRoute, DetailRoute("first"), BadgesRoute)

        router.resetTo(FinishedRoute)

        assertEquals(listOf(FinishedRoute), router.currentBackStack)
        assertFalse(router.pop())
    }

    @Test
    fun `popTo supports inclusive removal without removing root`() {
        val router = router(HomeRoute, DetailRoute("first"), BadgesRoute)

        assertTrue(router.popTo<DetailRoute>(inclusive = true))
        assertEquals(listOf(HomeRoute), router.currentBackStack)
        assertFalse(router.popTo<HomeRoute>(inclusive = true))
        assertFalse(router.popTo<FinishedRoute>())
        assertEquals(listOf(HomeRoute), router.currentBackStack)
    }

    @Test
    fun `invalid initial stack is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { router() }
        assertThrows(IllegalArgumentException::class.java) { router(HomeRoute, HomeRoute) }
        assertThrows(IllegalArgumentException::class.java) {
            NavigationRouter(NavBackStack<NavKey>(ForeignKey))
        }
    }

    @Test
    fun `invalid replacement does not corrupt the current flow`() {
        val router = router(HomeRoute, BadgesRoute)

        assertThrows(IllegalArgumentException::class.java) { router.replaceAll() }
        assertThrows(IllegalArgumentException::class.java) {
            router.replaceAll(HomeRoute, HomeRoute)
        }

        assertEquals(listOf(HomeRoute, BadgesRoute), router.currentBackStack)
    }

    @Test
    fun `route arguments survive serialization and restore navigation`() {
        val original = DetailRoute("trip/서울?day=1&stop=충전소")
        val restored = Json.decodeFromString<DetailRoute>(Json.encodeToString(original))
        val router = router(HomeRoute, restored)

        assertEquals(original, router.currentEntry)
        assertTrue(router.pop())
        assertEquals(HomeRoute, router.currentEntry)
    }

    private fun router(vararg routes: AppRoute): NavigationRouter =
        NavigationRouter(NavBackStack<NavKey>(*routes))
}

@Serializable
private data object HomeRoute : AppRoute

@Serializable
private data class DetailRoute(val tripId: String) : AppRoute

@Serializable
private data object BadgesRoute : AppRoute

@Serializable
private data object FinishedRoute : AppRoute

private data object ForeignKey : NavKey
