package io.issac.pleosandroid.feature.badges.album

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.cards.AlbumSnapshot
import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.domain.cards.CardGrade
import io.issac.pleosandroid.core.domain.cards.CardPrint
import io.issac.pleosandroid.core.domain.cards.CopyStatus
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import io.issac.pleosandroid.core.domain.cards.ForgeReceipt
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.cards.PrintGroup
import io.issac.pleosandroid.core.domain.cards.ReceivedPack
import io.issac.pleosandroid.feature.badges.album.forge.ForgePlayback
import java.io.File
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ForgeWorkshopMotionTest {
    private val systemMotion = object : MotionDurationScale {
        override var scaleFactor = 1f
    }

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(effectContext = systemMotion)
    private val pack = ReceivedPack("pack", PackType.Busan, "방문 보상", Instant.EPOCH)
    private val print = CardPrint(
        "busan-haeundae", "해운대의 파도", PackType.Busan, PrintGroup.Beach, CardGrade.Common,
        "print_busan_haeundae_base", "print_busan_haeundae_variant", "해운대해수욕장", "BUSAN / 001",
    )
    private val screen = Rect()
    private var entry by mutableStateOf(0)
    private var attemptCount = 0
    private var example by mutableStateOf(trial("success", 12, 13))
    private var receipt by mutableStateOf<ForgeReceipt?>(null)
    private val stage get() = compose.onNodeWithTag("forge-theatre")
    private val title get() = compose.onNodeWithTag("forge-result-mark")
    private val next get() = compose.onNodeWithTag("forge-next")

    private fun trial(id: String, before: Int, after: Int, outcome: ForgeOutcome = ForgeOutcome.Success) =
        ForgeReceipt(id, "copy", before, after, outcome, 500, Instant.EPOCH, false, null)

    private fun mount(split: Boolean = false): StateRestorationTester {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            Box(
                Modifier.requiredSize(if (split) 480.dp else 960.dp, 568.dp).testTag("viewport").onGloballyPositioned {
                    val pt = it.positionInWindow()
                    screen.set(
                        pt.x.roundToInt(),
                        pt.y.roundToInt(),
                        pt.x.roundToInt() + it.size.width,
                        pt.y.roundToInt() + it.size.height,
                    )
                },
            ) {
                PleosTheme {
                    key(entry, example.id) {
                        val trace = receipt?.outcome == ForgeOutcome.Destroyed
                        val card =
                            CardCopy(
                                "copy", print, true, receipt?.after ?: example.before,
                                maxOf(
                                    example.before,
                                    example.after,
                                ),
                                if (trace) CopyStatus.Trace else CopyStatus.Owned, 0, pack, Instant.EPOCH, true,
                            )
                        val snapshot = AlbumSnapshot(200000, null, listOf(pack), listOf(card), listOfNotNull(receipt))
                        CardWorkshop(card, snapshot, false, {
                            when (it) {
                                is AlbumIntent.Enhance -> {
                                    attemptCount++
                                    receipt = example.copy(id = it.requestId)
                                }

                                is AlbumIntent.Acknowledge -> receipt = null

                                else -> error("Unexpected action")
                            }
                        }, {}, {}, Modifier.fillMaxSize())
                    }
                }
            }
        }
        compose.waitUntil(20_000) { runCatching { compose.onNodeWithTag("forge-attempt").assertIsEnabled() }.isSuccess }
        compose.mainClock.autoAdvance = false
        return restoration
    }

    @Test fun everyResultAppearsInTheMiddleBeforeTheReceiptCanBeDismissed() {
        val outcomes = listOf(
            trial("hold", 15, 15, ForgeOutcome.Hold),
            trial("drop", 15, 14, ForgeOutcome.Drop),
            trial("destroy", 15, 10, ForgeOutcome.Destroyed),
            trial("success", 12, 13),
            trial("great", 9, 11, ForgeOutcome.GreatSuccess),
        )
        val milestones = listOf(5, 10, 15, 20, 25, 26, 27, 28).map { trial("level-$it", it - 1, it) }
        val capture = InstrumentationRegistry.getArguments().getString("forgeCapture")
        val cases = when (capture) {
            "outcomes" -> outcomes

            "setbacks" -> {
                val target = InstrumentationRegistry.getArguments().getString("forgeOutcome")
                outcomes.filter {
                    it.after <= it.before && (target == null || target == it.id)
                }.also { check(it.isNotEmpty()) }
            }

            "milestones" -> milestones

            "etching" -> outcomes.filter {
                it.outcome == ForgeOutcome.Success || it.outcome == ForgeOutcome.GreatSuccess
            } +
                milestones.filter { it.after <= 25 }

            else -> outcomes + milestones
        }
        mount()
        for (case in cases) {
            compose.runOnIdle {
                example = case
                receipt = null
            }
            compose.mainClock.advanceTimeBy(96)
            val button = readyButton()
            if (capture != null) captureFrame(case.id, 0)
            button.performClick()
            compose.mainClock.advanceTimeBy(32)
            stage.assertIsDisplayed()
            title.assertDoesNotExist()
            next.assertDoesNotExist()
            val playback = ForgePlayback(case)
            val shown = mutableListOf<ForgeOutcome>()
            var frame = 1
            val step = when (capture) {
                null -> 160L
                "setbacks" -> 32L
                else -> 48L
            }
            var elapsed = 32L
            while (elapsed < playback.durationMs + step * 2) {
                compose.mainClock.advanceTimeBy(step)
                elapsed += step
                val time = elapsed.toFloat() / playback.durationMs
                val sequence = playback.sequenceAt(time)
                val localMs = playback.localTime(sequence, time) * sequence.durationMs
                if (sequence.outcome !in shown && localMs > sequence.durationMs * sequence.titleAt + 210 &&
                    localMs < sequence.durationMs - 340
                ) {
                    title.assertIsDisplayed()
                        .assert(hasText(sequence.outcome.label))
                        .assert(hasText(sequence.levelLine))
                    next.assertDoesNotExist()
                    shown.add(sequence.outcome)
                }
                if (capture != null) captureFrame(case.id, frame++)
            }
            assertEquals(listOf(playback.first.outcome, case.outcome).distinct(), shown)
            stage.assertDoesNotExist()
            next.assertIsEnabled()
        }
        assertEquals(cases.size, attemptCount)
    }

    @Test fun skippingAndRecreatingInSplitScreenCannotSpendAgainOrLoseTheResult() {
        example = trial("bonus-on-ten", 8, 10, ForgeOutcome.GreatSuccess)
        val restoration = mount(split = true)
        readyButton().performClick()
        val first = ForgePlayback(example).first
        compose.mainClock.advanceTimeBy((first.durationMs * first.titleAt).toLong() + 250)
        title.assertIsDisplayed().assert(hasText("+8  →  +9"))
        compose.onNodeWithTag("forge-skip").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(64)
        stage.assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        next.performScrollTo().assertIsEnabled()
        restoration.emulateSavedInstanceStateRestore()
        compose.mainClock.advanceTimeBy(96)
        stage.assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        next.performScrollTo().assertIsEnabled().performClick()
        compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithTag("forge-attempt").assertIsEnabled()
        assertEquals(1, attemptCount)
    }

    @Test fun resizingDuringTheVerdictKeepsTheSameRevealAndRemainingTime() {
        example = trial("critical-over-ten", 9, 11, ForgeOutcome.GreatSuccess)
        val restore = mount()
        readyButton().performClick()
        val playback = ForgePlayback(example)
        val first = playback.first
        val played = (first.durationMs * first.titleAt).toLong() + 300
        compose.mainClock.advanceTimeBy(played)
        title.assertIsDisplayed().assert(hasText("강화 성공")).assert(hasText("+9  →  +10"))
        restore.emulateSavedInstanceStateRestore()
        compose.mainClock.advanceTimeBy(64)
        title.assertIsDisplayed().assert(hasText("+9  →  +10"))
        val bonusAt = first.durationMs + (playback.last.durationMs * playback.last.titleAt).toLong() + 300
        compose.mainClock.advanceTimeBy(bonusAt - played)
        title.assertIsDisplayed().assert(hasText("대성공")).assert(hasText("+10  →  +11"))
        compose.mainClock.advanceTimeBy(playback.durationMs - bonusAt + 96)
        next.assertIsEnabled()
        assertEquals(1, attemptCount)
    }

    @Test fun reenteringACompletedAttemptShowsItsReceiptWithoutReplayingOrChargingAgain() {
        mount()
        readyButton().performClick()
        compose.mainClock.advanceTimeBy(160)
        stage.assertIsDisplayed()
        compose.mainClock.advanceTimeBy(ForgePlayback(example).durationMs + 96L)
        next.assertIsEnabled()

        // Pop the workshop and open a fresh entry for the same still-pending receipt.
        compose.runOnIdle { entry++ }
        compose.mainClock.advanceTimeBy(96)
        stage.assertDoesNotExist()
        next.assertIsEnabled()
        assertEquals(1, attemptCount)
        next.performClick()
        compose.mainClock.advanceTimeBy(96)
        readyButton().performClick()
        compose.mainClock.advanceTimeBy(96)
        stage.assertIsDisplayed()
        assertEquals(2, attemptCount)
    }

    @Test fun reducedMotionImmediatelyShowsTheSavedResultAndTheNextAction() {
        example = trial("reduced-critical", 9, 11, ForgeOutcome.GreatSuccess)
        systemMotion.scaleFactor = 0f
        mount(split = true)
        readyButton().performClick()
        compose.mainClock.advanceTimeBy(96)
        stage.assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        next.performScrollTo().assertIsEnabled()
        assertEquals(1, attemptCount)
    }

    private fun readyButton(): androidx.compose.ui.test.SemanticsNodeInteraction {
        // Bring the action into a split viewport before freezing the animation clock.
        compose.mainClock.autoAdvance = true
        val button = compose.onNodeWithTag("forge-attempt").performScrollTo().assertIsEnabled()
        compose.mainClock.autoAdvance = false
        return button
    }

    private fun captureFrame(name: String, index: Int) {
        compose.waitForIdle()
        val root = if (InstrumentationRegistry.getArguments().getString("forgeCapture") ==
            "etching"
        ) {
            "forge-optics"
        } else {
            "forge-motion"
        }
        writeForgeFrame(compose.activity.window, screen, "$root/$name", index)
    }
}
