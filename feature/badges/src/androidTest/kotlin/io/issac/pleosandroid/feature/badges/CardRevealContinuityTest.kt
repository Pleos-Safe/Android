package io.issac.pleosandroid.feature.badges

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.feature.badges.model.CollectionCardDesign
import io.issac.pleosandroid.feature.badges.model.CollectionSeed
import io.issac.pleosandroid.feature.badges.model.Haeundae
import io.issac.pleosandroid.feature.badges.model.Huinnyeoul
import io.issac.pleosandroid.feature.badges.model.SafeHome
import io.issac.pleosandroid.feature.badges.reveal.CollectionRevealScreen
import io.issac.pleosandroid.feature.badges.reveal.motion.CardRevealMotion
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CardRevealContinuityTest {
    @get:Rule val compose = createComposeRule()
    private var card by mutableStateOf(CollectionSeed.cards.single { it.design == CollectionCardDesign.SafeHome })
    private var finished = false

    @Test
    fun theSameReflectionSurvivesBothEndsOfEveryFoilUpgrade() {
        show(wide = true)
        val print = compose.onNodeWithTag("collection-reveal-card")
        for (design in listOf(
            CollectionCardDesign.Haeundae,
            CollectionCardDesign.Huinnyeoul,
            CollectionCardDesign.SafeHome,
        )) {
            compose.runOnIdle {
                card = CollectionSeed.cards.single { it.design == design }
                finished = false
            }
            val motion = CardRevealMotion.forCard(card)
            compose.mainClock.advanceTimeBy(1300)
            val waiting = print.captureToImage().asAndroidBitmap()
            compose.onNodeWithText("변화 만나보기").performClick()
            compose.mainClock.advanceTimeByFrame()
            val starting = print.captureToImage().asAndroidBitmap()
            assertTrue("${design.id}: the starting reflection is continuous", difference(waiting, starting) < 2.5)

            compose.mainClock.advanceTimeBy(motion.durationMillis + 100L)
            compose.runOnIdle { assertTrue(finished) }
            val lastAnimationFrame = print.captureToImage().asAndroidBitmap()
            // Publish separately so we can compare the final animated print with its inspectable successor.
            compose.runOnIdle { card = card.copy(revealedLevel = card.earnedLevel) }
            compose.mainClock.advanceTimeByFrame()
            val published = print.captureToImage().asAndroidBitmap()
            assertTrue(
                "${design.id}: publishing does not reset the foil or its pose",
                difference(lastAnimationFrame, published) < 2.5,
            )
            if (card.earnedLevel == 3) {
                compose.mainClock.advanceTimeBy(350)
                assertTrue(
                    "Silver does not replay its entry sweep",
                    published.sameAs(print.captureToImage().asAndroidBitmap()),
                )
            }
        }
    }

    @Test
    fun resultTextSmoothlyMakesRoomByShrinkingTheCardInSplitView() {
        card = CollectionSeed.cards.single { it.design == CollectionCardDesign.Haeundae }
        show(wide = false)
        val print = compose.onNodeWithTag("collection-reveal-card")
        compose.mainClock.advanceTimeByFrame()
        val waiting = print.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("변화 만나보기").performClick()
        compose.mainClock.advanceTimeByFrame()
        val starting = print.fetchSemanticsNode().boundsInRoot
        assertEquals("Starting keeps the card's width", waiting.width, starting.width, 1f)
        assertEquals("Starting keeps the card's position", waiting.top, starting.top, 1f)
        compose.mainClock.advanceTimeBy(CardRevealMotion.Tide.durationMillis + 100L)
        val before = print.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { card = card.copy(revealedLevel = card.earnedLevel) }
        compose.mainClock.advanceTimeByFrame()
        val first = print.fetchSemanticsNode().boundsInRoot
        assertEquals("The result starts at the previous width", before.width, first.width, 2f)
        val sizes = mutableListOf(first.width)
        repeat(9) {
            compose.mainClock.advanceTimeBy(80)
            sizes += print.fetchSemanticsNode().boundsInRoot.width
        }
        val shrink = before.width - sizes.last()
        assertTrue("The extra result caption must have taken space", shrink > 8f)
        assertTrue(
            "The width passes through intermediate sizes",
            sizes.drop(1).dropLast(2).any {
                it < before.width - 2f && it > sizes.last() + 2f
            },
        )
        assertTrue(
            "There is no single-frame size snap",
            sizes.zipWithNext().all { (a, b) ->
                abs(a - b) < shrink * .55f + 1f
            },
        )
    }

    private fun show(wide: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PleosTheme {
                CollectionRevealScreen(
                    card,
                    {},
                    {},
                    {},
                    {},
                    onComplete = {
                        finished = true
                        true
                    },
                    canOpen = { true },
                    modifier = Modifier.requiredSize(if (wide) 1100.dp else 840.dp, if (wide) 592.dp else 1100.dp),
                )
            }
        }
    }

    private fun difference(a: Bitmap, b: Bitmap): Double {
        assertEquals(a.width, b.width)
        assertEquals(a.height, b.height)
        var sum = 0L
        var samples = 0
        for (y in 0 until a.height step 2) {
            for (x in 0 until a.width step 2) {
                val p = a.getPixel(x, y)
                val q = b.getPixel(x, y)
                sum +=
                    abs(Color.red(p) - Color.red(q)) + abs(Color.green(p) - Color.green(q)) +
                    abs(Color.blue(p) - Color.blue(q))
                samples++
            }
        }
        return sum.toDouble() / (samples * 3)
    }
}
