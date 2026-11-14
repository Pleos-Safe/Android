package io.issac.pleosandroid.feature.main

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.navigation.BadgesRoute
import io.issac.pleosandroid.core.navigation.EnhancementRoute
import io.issac.pleosandroid.core.navigation.ForgeMotionRoute
import io.issac.pleosandroid.core.navigation.MotionGalleryRoute
import io.issac.pleosandroid.core.navigation.PackMotionRoute
import io.issac.pleosandroid.core.navigation.PackShelfRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class CardPreviewStackMigrationTest {
    @Test fun restoresEachTabToItsLastAvailableScreen() {
        val collection = NavBackStack<NavKey>(
            BadgesRoute,
            PackShelfRoute,
            MotionGalleryRoute(),
            PackMotionRoute(PackType.Seoul),
        )
        val forge = NavBackStack<NavKey>(
            EnhancementRoute,
            MotionGalleryRoute(startOnForge = true),
            ForgeMotionRoute(4, 5, ForgeOutcome.Success),
        )

        removeUnavailableCardPreviews(collection)
        removeUnavailableCardPreviews(forge)

        assertEquals(listOf(BadgesRoute, PackShelfRoute), collection.toList())
        assertEquals(listOf(EnhancementRoute), forge.toList())
    }
}
