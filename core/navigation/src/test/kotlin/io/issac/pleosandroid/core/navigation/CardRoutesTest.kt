package io.issac.pleosandroid.core.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavKeySerializer
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import io.issac.pleosandroid.core.domain.cards.PackType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardRoutesTest {
    @Test fun restoresThePreviouslyShippedClassNamesAndMissingOptionalGrade() {
        val old = "io.issac.pleosandroid.feature.badges.album"
        fun restore(name: String, fields: String): NavKey {
            val saved = buildJsonObject {
                put("type", "$old.$name")
                put("value", Json.parseToJsonElement(fields))
            }
            return Json.decodeFromJsonElement(NavKeySerializer<NavKey>(), saved)
        }
        assertEquals(PackOddsRoute, restore("PackOddsRoute", "{}"))
        assertEquals(CardDetailRoute("copy"), restore("AlbumDetailRoute", """{"copyId":"copy"}"""))
        assertEquals(WorkshopRoute("copy"), restore("WorkshopRoute", """{"copyId":"copy"}"""))
        assertEquals(MotionGalleryRoute(true), restore("gallery.MotionGalleryRoute", """{"startOnForge":true}"""))
        assertEquals(
            PackMotionRoute(PackType.Busan, flipOnly = true),
            restore("gallery.CardMotion\$Pack", """{"region":"Busan","flipOnly":true}"""),
        )
        assertEquals(
            ForgeMotionRoute(14, 16, ForgeOutcome.GreatSuccess),
            restore("gallery.CardMotion\$Forge", """{"before":14,"after":16,"outcome":"GreatSuccess"}"""),
        )
    }

    @Test fun onlyTheActiveParkedSourceCanReturnFocusAndOnlyTheMatchingCardConsumesIt() {
        val source = WorkshopRoute("copy")
        val entry = NavBackStack<NavKey>(EnhancementRoute, source)
        val collection = NavBackStack<NavKey>(BadgesRoute, PackShelfRoute)
        var allowed = false
        val router = NavigationRouter(entry, selectTab = { collection }, canNavigate = { allowed })
        val result = CollectionFocusResult(mutableStateOf(null))
        result.showCard(router, source, "copy")
        assertNull(result.copyId)
        allowed = true
        result.showCard(router, OpenPackRoute("other"), "copy")
        assertNull(result.copyId)

        result.showCard(router, source, "copy")
        assertEquals(listOf(BadgesRoute), collection.toList())
        result.consumed("other")
        assertEquals("copy", result.copyId)
        result.consumed("copy")
        assertNull(result.copyId)
    }
}
