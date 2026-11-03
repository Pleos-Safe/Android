package io.issac.pleosandroid.feature.nav

import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.core.navigation.NavEntryResult
import kotlinx.serialization.Serializable
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NavEntryFactoryTest {
    @Test
    fun `only the factory that owns a route provides its entry`() {
        val expected = NavEntryResult(metadata = mapOf("owner" to "setup")) { }
        val unrelated = NavEntryFactory { null }
        val owner = NavEntryFactory { route -> if (route == FactoryTestRoute) expected else null }

        val result = resolveNavEntryResult(FactoryTestRoute, setOf(unrelated, owner))

        assertSame(expected, result)
    }

    @Test
    fun `unregistered route fails with a useful error`() {
        val error = assertThrows(IllegalStateException::class.java) {
            resolveNavEntryResult(FactoryTestRoute, setOf(NavEntryFactory { null }))
        }

        assertTrue(error.message.orEmpty().contains("No NavEntryFactory"))
        assertTrue(error.message.orEmpty().contains("FactoryTestRoute"))
    }

    @Test
    fun `two factories owning the same route are rejected`() {
        val first = NavEntryFactory { NavEntryResult { } }
        val second = NavEntryFactory { NavEntryResult { } }

        val error = assertThrows(IllegalStateException::class.java) {
            resolveNavEntryResult(FactoryTestRoute, setOf(first, second))
        }

        assertTrue(error.message.orEmpty().contains("Multiple NavEntryFactory"))
    }
}

@Serializable
private data object FactoryTestRoute : AppRoute
