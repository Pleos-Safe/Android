package io.issac.pleosandroid.feature.collection.gallery

import io.issac.pleosandroid.core.cardui.preview.previewPackCard
import io.issac.pleosandroid.core.domain.cards.CardGrade
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.navigation.ForgeMotionRoute
import io.issac.pleosandroid.core.navigation.PackMotionRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionGalleryCoverageTest {
    @Test fun everyPackRevealForgeOutcomeAndFrameHasAPlayableExample() {
        val scenes = MotionGroup.entries.flatMap { motionChoices(it, 14) }.filter { it.enabled }.map { it.scene }
        val packs = scenes.filterIsInstance<PackMotionRoute>()
        assertEquals(PackType.entries.toSet(), packs.filterNot { it.flipOnly }.map { it.region }.toSet())
        assertEquals(
            CardGrade.entries.toSet(),
            packs.filter {
                it.flipOnly
            }.map { previewPackCard(it.region, it.grade).grade }.toSet(),
        )
        val forge = scenes.filterIsInstance<ForgeMotionRoute>()
        assertEquals(ForgeOutcome.entries.toSet(), forge.map { it.outcome }.toSet())
        for (outcome in listOf(ForgeOutcome.Hold, ForgeOutcome.Destroyed)) {
            val preview = motionChoices(MotionGroup.Outcomes, 14).single {
                (it.scene as ForgeMotionRoute).outcome == outcome
            }
            assertTrue(preview.enabled)
            assertTrue(preview.detail.contains("실제 발생 확률 0%"))
        }
        assertEquals(
            listOf(5, 10, 15, 20, 25, 26, 27, 28),
            motionChoices(MotionGroup.Milestones, 14).map {
                (it.scene as ForgeMotionRoute).after
            },
        )
    }

    @Test fun selectedLevelCanCrossAFrameOnEitherBeatOfACritical() {
        fun criticalAt(level: Int) = motionChoices(MotionGroup.Outcomes, level).single {
            (it.scene as ForgeMotionRoute).outcome == ForgeOutcome.GreatSuccess
        }
        assertEquals("+8 → +9 → +10", criticalAt(8).detail)
        assertEquals("+9 → +10 → +11", criticalAt(9).detail)
        assertTrue(criticalAt(19).enabled)
        assertFalse(criticalAt(20).enabled)
    }
}
