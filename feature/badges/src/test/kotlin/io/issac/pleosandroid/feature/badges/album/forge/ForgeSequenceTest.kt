package io.issac.pleosandroid.feature.badges.album.forge

import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import io.issac.pleosandroid.core.domain.cards.ForgeReceipt
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeSequenceTest {
    private fun sequence(before: Int, after: Int, outcome: ForgeOutcome = ForgeOutcome.Success) =
        ForgeSequence(before, after, outcome)

    private fun critical(before: Int): ForgePlayback {
        val receipt = ForgeReceipt(
            "attempt", "copy", before, before + 2, ForgeOutcome.GreatSuccess, 0, Instant.EPOCH, false, null,
        )
        return ForgePlayback(receipt)
    }

    @Test fun everyPlateGetsItsOwnLongerRevealAndTwoStepCriticalsUseTheCrossedPlate() {
        val steps = (5..25 step 5).map { sequence(it - 1, it) }
        assertEquals(
            listOf(ForgeFinish.Metal, ForgeFinish.Prism, ForgeFinish.FullArt, ForgeFinish.Crown, ForgeFinish.Laurel),
            steps.map {
                it.finish
            },
        )
        assertTrue(steps.zipWithNext().all { (lo, hi) -> hi.durationMs > lo.durationMs })
        for (milestone in listOf(5, 10, 15, 20)) {
            val onFirst = critical(milestone - 1)
            val onBonus = critical(milestone - 2)
            assertEquals(sequence(milestone - 1, milestone).finish, onFirst.first.finish)
            assertEquals(ForgeFinish.Print, onFirst.last.finish)
            assertEquals(ForgeFinish.Print, onBonus.first.finish)
            assertEquals(onFirst.first.finish, onBonus.last.finish)
        }
        assertTrue(critical(13).last.expandsArt)
        assertFalse(critical(13).first.expandsArt)
        assertTrue(sequence(15, 14, ForgeOutcome.Drop).contractsArt)
    }

    @Test fun aCriticalHoldsTheOrdinarySuccessOnScreenBeforeTheExtraLevelMoves() {
        val playback = critical(9)
        val first = playback.first
        val bonus = playback.last
        assertEquals(ForgeOutcome.Success, first.outcome)
        assertEquals(10, first.after)
        assertEquals(ForgeOutcome.GreatSuccess, bonus.outcome)
        assertEquals(first.after, bonus.before)
        assertEquals(11, bonus.after)
        assertEquals(sequence(9, 10).durationMs, first.durationMs)

        val beforeBonus = (first.durationMs - 1f) / playback.durationMs
        val afterHandoff = (first.durationMs + 1f) / playback.durationMs
        assertEquals(first, playback.sequenceAt(beforeBonus))
        assertEquals(1f, first.exposure(playback.localTime(first, beforeBonus)), 0f)
        assertEquals(bonus, playback.sequenceAt(afterHandoff))
        assertEquals(0f, bonus.exposure(playback.localTime(bonus, afterHandoff)), 0f)
        assertEquals(1f, playback.enter(afterHandoff), 0f)
        assertEquals(0f, playback.exit(afterHandoff), 0f)
    }

    @Test fun uncappedGainsUseSkyFinishesWithoutInventingAnotherPlateOrCelebratingLosses() {
        assertEquals(
            listOf(SkyFinish.Eclipse, SkyFinish.Aurora, SkyFinish.Constellation),
            (26..28).map {
                sequence(it - 1, it).sky
            },
        )
        assertEquals(ForgeFinish.Beyond, sequence(29, 30).finish)
        assertEquals(ForgeFinish.Print, sequence(27, 27, ForgeOutcome.Hold).finish)
        assertEquals(ForgeFinish.Print, sequence(27, 22, ForgeOutcome.Destroyed).finish)
        assertFalse(sequence(29, 30).expandsArt)
        assertTrue(sequence(10000, 10001).durationMs < 6500)
    }

    @Test fun crossingTheFullCoverBoundaryUsesOneContinuousExposureInEitherDirection() {
        val up = sequence(14, 15)
        assertTrue(up.expandsArt)
        assertFalse(up.contractsArt)
        for (down in listOf(sequence(15, 14, ForgeOutcome.Drop), sequence(18, 13, ForgeOutcome.Destroyed))) {
            assertTrue(down.contractsArt)
            assertFalse(down.expandsArt)
            assertEquals(0f, down.exposure(0f), 0f)
            assertEquals(1f, down.exposure(1f), 0f)
            val frames = (0..200).map { down.exposure(it / 200f) }
            assertTrue(frames.zipWithNext().all { (a, b) -> b >= a })
            assertTrue(frames.count { it > 0.1f && it < 0.9f } > 5)
        }
        assertFalse(sequence(21, 16, ForgeOutcome.Destroyed).contractsArt)
        for (u in listOf(0f, 1f)) {
            assertEquals(0f, holdCompression(u) + holdRebound(u), .0001f)
            assertEquals(0f, fallDisplacement(u), .0001f)
            assertEquals(0f, fractureShock(u), .0001f)
        }
    }

    @Test fun suspenseDoesNotSignalWhichResultWasDrawn() {
        val cases =
            listOf(
                sequence(14, 15),
                critical(14).first,
                sequence(14, 14, ForgeOutcome.Hold),
                sequence(14, 13, ForgeOutcome.Drop),
                sequence(14, 9, ForgeOutcome.Destroyed),
            )
        for (ms in listOf(140, 650, 1025, 1510)) {
            val first = cases.first()
            val referenceTime = ms.toFloat() / first.durationMs
            for (trial in cases) {
                val t = ms.toFloat() / trial.durationMs
                assertEquals(first.charge(referenceTime), trial.charge(t), .0001f)
                assertEquals(first.gathering(referenceTime), trial.gathering(t), .0001f)
                assertEquals(first.chargeShake(referenceTime).x, trial.chargeShake(t).x, .0001f)
                assertEquals(first.chargeShake(referenceTime).y, trial.chargeShake(t).y, .0001f)
                assertEquals(0f, trial.release(t), 0f)
            }
        }
    }

    @Test fun theCardCatchesLightWhileTheVerdictIsVisibleAndReturnsToFlatAtRest() {
        val great = critical(9).last
        val look = (0..10).map { great.cardRotation(great.titleAt + (.93f - great.titleAt) * it / 10f).x }
        assertTrue(look.max() - look.min() > 5f)
        assertTrue(kotlin.math.abs(great.cardRotation(1f).x) < .001f)
        assertFalse(great.etchesFrame)
        assertFalse(sequence(25, 26).etchesFrame)
        assertFalse(sequence(15, 14, ForgeOutcome.Drop).etchesFrame)
    }
}
