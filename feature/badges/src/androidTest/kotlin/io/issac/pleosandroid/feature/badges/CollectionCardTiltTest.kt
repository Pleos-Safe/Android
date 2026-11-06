package io.issac.pleosandroid.feature.badges

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.InspectableMilestoneCard
import io.issac.pleosandroid.core.designsystem.component.MilestoneCard
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.feature.badges.model.CollectionCardDesign
import io.issac.pleosandroid.feature.badges.model.CollectionSeed
import io.issac.pleosandroid.feature.badges.model.Haeundae
import io.issac.pleosandroid.feature.badges.model.Huinnyeoul
import io.issac.pleosandroid.feature.badges.model.SafeHome
import io.issac.pleosandroid.feature.badges.reveal.CollectionRevealScreen
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class CollectionCardTiltTest {
    private val systemScale = object : MotionDurationScale {
        override var scaleFactor = 1f
    }

    @get:Rule val compose = createComposeRule(effectContext = systemScale)
    private val playsLight = SemanticsMatcher("plays the card light") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == "카드에 빛 비추기"
    }

    @Test
    fun reducedMotionChangesTheLightOnThePrintWithoutMovingThePaper() {
        systemScale.scaleFactor = 0f
        var grade by mutableIntStateOf(3)
        var design by mutableStateOf(CollectionCardDesign.Haeundae)
        compose.setContent { PleosTheme { InspectableMilestoneCard(design.front(grade), Modifier.width(300.dp)) } }
        val print = compose.onNode(playsLight)
        val cases = listOf(
            3 to CollectionCardDesign.Haeundae,
            4 to CollectionCardDesign.Huinnyeoul,
            5 to CollectionCardDesign.SafeHome,
        )
        for ((level, card) in cases) {
            compose.runOnIdle {
                grade = level
                design = card
            }
            val title = compose.onNodeWithText(card.title, useUnmergedTree = true)
            val titleBounds = title.fetchSemanticsNode().boundsInRoot
            val before = print.captureToImage().asAndroidBitmap()
            print.performClick()
            val after = print.captureToImage().asAndroidBitmap()
            assertTrue("Lv.$level • the art reflects a different angle", changedPixels(before, after, .12f, .70f) > 100)
            assertEquals("Lv.$level • the title stays in place", titleBounds, title.fetchSemanticsNode().boundsInRoot)
            if (level == 3) {
                // Silver catches the artwork and metal stamps; the unprinted stock stays matte.
                assertEquals(
                    "The silver card keeps matte stock",
                    0,
                    changedPixels(before, after, .80f, .90f, .12f, .60f),
                )
            } else {
                assertTrue(
                    "Lv.$level • the foil continues across the paper",
                    changedPixels(before, after, .80f, .90f) > 30,
                )
            }
        }
    }

    @Test
    fun onlySapphireAndOnyxCatchAmbientLightOnTheShelf() {
        compose.mainClock.autoAdvance = false
        var level by mutableIntStateOf(1)
        compose.setContent {
            PleosTheme {
                MilestoneCard(
                    CollectionCardDesign.Haeundae.front(level),
                    Modifier.width(280.dp).testTag("shelf"),
                )
            }
        }
        val print = compose.onNodeWithTag("shelf")
        for (grade in 1..5) {
            compose.runOnIdle { level = grade }
            compose.mainClock.advanceTimeBy(100)
            val a = print.captureToImage().asAndroidBitmap()
            compose.mainClock.advanceTimeBy(1800)
            val b = print.captureToImage().asAndroidBitmap()
            assertEquals("Lv.$grade ambient finish", grade < 4, a.sameAs(b))
        }
    }

    @Test
    fun horizontalDragTakesOverTheSweepAndHoldsTheChosenAngle() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PleosTheme { InspectableMilestoneCard(CollectionCardDesign.Huinnyeoul.front(4), Modifier.width(300.dp)) }
        }
        val print = compose.onNode(playsLight)
        compose.mainClock.advanceTimeBy(650)
        print.performTouchInput { swipe(center, Offset(width * .13f, centerY), 250) }
        compose.mainClock.advanceTimeByFrame()
        val held = print.captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(2600)
        assertTrue("Releasing the card keeps its new pose", held.sameAs(print.captureToImage().asAndroidBitmap()))
        print.performTouchInput { swipe(Offset(width * .15f, centerY), Offset(width * .78f, centerY), 350) }
        compose.mainClock.advanceTimeByFrame()
        assertTrue(
            "Dragging in the opposite direction moves the finish",
            !held.sameAs(print.captureToImage().asAndroidBitmap()),
        )
    }

    @Test
    fun theUpgradeResultOffersTiltOnlyAfterPublishingLevelThree() {
        systemScale.scaleFactor = 0f
        var card by mutableStateOf(CollectionSeed.cards.single { it.design == CollectionCardDesign.Haeundae })
        compose.setContent {
            PleosTheme {
                CollectionRevealScreen(card, {}, {}, {}, {}, { level ->
                    card = card.copy(revealedLevel = level)
                    true
                }, { true })
            }
        }
        compose.onNode(playsLight).assertDoesNotExist()
        compose.onNodeWithText("변화 만나보기").performClick()
        compose.onNodeWithText("컬렉션에서 보기").assertExists()
        compose.onNode(playsLight).assertExists()
    }

    private fun changedPixels(
        a: Bitmap,
        b: Bitmap,
        from: Float,
        to: Float,
        left: Float = .125f,
        right: Float = .875f,
    ): Int {
        var count = 0
        for (y in (a.height * from).toInt() until (a.height * to).toInt() step 3) {
            for (x in (a.width * left).toInt() until (a.width * right).toInt() step 3) {
                val p = a.getPixel(x, y)
                val q = b.getPixel(x, y)
                val difference =
                    abs(Color.red(p) - Color.red(q)) + abs(Color.green(p) - Color.green(q)) +
                        abs(Color.blue(p) - Color.blue(q))
                if (difference > 18) count++
            }
        }
        return count
    }
}
