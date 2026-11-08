package io.issac.pleosandroid.feature.badges.album.gallery

import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.feature.badges.album.forge.ForgeFinish
import io.issac.pleosandroid.feature.badges.album.forge.ForgeSequence
import io.issac.pleosandroid.feature.badges.album.forge.SkyFinish
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionGalleryCoverageTest {
    @Test fun everyPackRevealForgeOutcomeAndFrameHasAPlayableExample() {
        val scenes = MotionGroup.entries.flatMap { motionChoices(it, 14) }.filter { it.enabled }.map { it.scene }
        val packs = scenes.filterIsInstance<CardMotion.Pack>()
        assertEquals(PackType.entries.toSet(), packs.filterNot { it.flipOnly }.map { it.region }.toSet())
        assertTrue(packs.any { it.flipOnly })
        val forge = scenes.filterIsInstance<CardMotion.Forge>()
        assertEquals(ForgeOutcome.entries.toSet(), forge.map { it.outcome }.toSet())
        val steps = forge.map { ForgeSequence(it.before, it.after, it.outcome) }
        assertEquals(ForgeFinish.entries.toSet(), steps.map { it.finish }.toSet())
        assertEquals(SkyFinish.entries.toSet(), steps.mapNotNull { it.sky }.toSet())
    }

    @Test fun selectedLevelCanCrossAFrameOnEitherBeatOfACritical() {
        fun criticalAt(level: Int) = motionChoices(MotionGroup.Outcomes, level).single {
            (it.scene as CardMotion.Forge).outcome == ForgeOutcome.GreatSuccess
        }
        assertEquals("+8 → +9 → +10", criticalAt(8).detail)
        assertEquals("+9 → +10 → +11", criticalAt(9).detail)
        assertTrue(criticalAt(19).enabled)
        assertFalse(criticalAt(20).enabled)
    }
}
