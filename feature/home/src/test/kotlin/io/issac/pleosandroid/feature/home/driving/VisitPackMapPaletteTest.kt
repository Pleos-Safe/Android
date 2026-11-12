package io.issac.pleosandroid.feature.home.driving

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitPackMapPaletteTest {
    @Test
    fun everyThreePacksAdvanceOneLevel() {
        val expected = mapOf(
            1 to 1, 3 to 1,
            4 to 2, 6 to 2,
            7 to 3, 9 to 3,
            10 to 4, 12 to 4,
            13 to 5, 15 to 5,
            16 to 6,
        )
        for ((count, step) in expected) {
            assertEquals("visit packs: $count", step, VisitPackMapPalette.stepFor(count))
        }
    }

    @Test
    fun emptyAndInvalidCountsKeepTheUnvisitedColor() {
        val unvisited = Color(0xFF192D43)
        for (count in listOf(Int.MIN_VALUE, -1, 0)) {
            assertEquals(0, VisitPackMapPalette.stepFor(count))
            assertEquals(unvisited, VisitPackMapPalette.colorFor(count, unvisited))
        }
    }

    @Test
    fun largeCountsStayAtTheLastLevelWithoutOverflow() {
        val unvisited = Color.Black
        val last = VisitPackMapPalette.colorFor(16, unvisited)
        for (count in listOf(17, 18, 19, 1_000, Int.MAX_VALUE)) {
            assertEquals(6, VisitPackMapPalette.stepFor(count))
            assertEquals(last, VisitPackMapPalette.colorFor(count, unvisited))
        }
    }

    @Test
    fun eachLevelIsOpaqueAndDarkerThanThePreviousLevel() {
        val levels = listOf(1, 4, 7, 10, 13, 16).map { VisitPackMapPalette.colorFor(it, Color.Black) }
        assertEquals(6, levels.distinct().size)
        assertTrue(levels.all { it.alpha == 1f })
        for ((previous, next) in levels.zipWithNext()) {
            assertTrue("Each visit-pack level must become darker", next.luminance() < previous.luminance())
        }
    }
}
