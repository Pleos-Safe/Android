package io.issac.pleosandroid.feature.badges

import android.graphics.Bitmap
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.feature.badges.model.Cheongsapo
import io.issac.pleosandroid.feature.badges.model.CollectionCard
import io.issac.pleosandroid.feature.badges.model.CollectionCardDesign
import io.issac.pleosandroid.feature.badges.model.CollectionSeed
import io.issac.pleosandroid.feature.badges.model.Haeundae
import io.issac.pleosandroid.feature.badges.model.Huinnyeoul
import io.issac.pleosandroid.feature.badges.model.SafeHome
import io.issac.pleosandroid.feature.badges.model.Songdo
import io.issac.pleosandroid.feature.badges.reveal.CollectionRevealScreen
import io.issac.pleosandroid.feature.badges.reveal.motion.CardRevealMotion
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class CardRevealMotionTest {
    private val systemScale = object : MotionDurationScale {
        override var scaleFactor = 1f
    }

    @get:Rule val compose = createComposeRule(effectContext = systemScale)
    private lateinit var lifecycle: LifecycleRegistry
    private val completed = mutableListOf<Int>()
    private var allowed = true
    private var backPresses = 0
    private var savedFrames = 0

    @Test
    fun newCardFlipsBeforeItsIdentityAndActionsArePublished() {
        verifyReveal(CollectionCardDesign.Songdo, "카드 열기", CardRevealMotion.Flip)
    }

    @Test
    fun levelTwoWaitsForTheOriginalFoilWipe() {
        verifyReveal(CollectionCardDesign.Cheongsapo, "변화 만나보기", CardRevealMotion.Foil)
    }

    @Test
    fun levelThreeWaitsForTheTidalReflection() {
        verifyReveal(CollectionCardDesign.Haeundae, "변화 만나보기", CardRevealMotion.Tide)
    }

    @Test
    fun levelFourWaitsForTheOpalRecoil() {
        verifyReveal(CollectionCardDesign.Huinnyeoul, "변화 만나보기", CardRevealMotion.Opal)
    }

    @Test
    fun levelFiveWaitsForTheGoldRecoil() {
        verifyReveal(CollectionCardDesign.SafeHome, "변화 만나보기", CardRevealMotion.Gold)
    }

    @Test
    fun backDuringRecoilLeavesTheCardPending() {
        show(CollectionCardDesign.SafeHome)
        compose.onNodeWithText("변화 만나보기").performClick()
        compose.mainClock.advanceTimeBy(3000)
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.mainClock.advanceTimeBy(2500)
        compose.runOnIdle {
            assertEquals(1, backPresses)
            assertEquals(emptyList<Int>(), completed)
        }
        compose.onNodeWithText("열어볼 준비가 됐어요").assertExists()
    }

    @Test
    fun losingParkingDuringTheGoldRecoilRequiresANewPress() {
        show(CollectionCardDesign.SafeHome)
        compose.onNodeWithText("변화 만나보기").performClick()
        compose.mainClock.advanceTimeBy(3100)
        compose.runOnIdle { allowed = false }
        compose.mainClock.advanceTimeBy(2000)
        compose.runOnIdle { allowed = true }
        compose.onNodeWithText("열어볼 준비가 됐어요").assertExists()
        compose.runOnIdle { assertEquals(emptyList<Int>(), completed) }
        compose.onNodeWithText("변화 만나보기").performClick()
        compose.mainClock.advanceTimeBy(CardRevealMotion.Gold.durationMillis + 160L)
        compose.runOnIdle { assertEquals(listOf(5), completed) }
    }

    @Test
    fun leavingTheForegroundOrDeferringNeverFinishesAnInterruptedFlip() {
        show(CollectionCardDesign.Songdo)
        compose.onNodeWithText("카드 열기").performClick()
        compose.mainClock.advanceTimeBy(960)
        compose.runOnIdle { lifecycle.currentState = Lifecycle.State.STARTED }
        compose.mainClock.advanceTimeBy(2000)
        compose.runOnIdle { lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithText("송도의 푸른 길").assertDoesNotExist()
        compose.onNodeWithText("카드 열기").performClick()
        compose.mainClock.advanceTimeBy(256)
        compose.onNodeWithText("나중에 열기").performClick()
        compose.mainClock.advanceTimeBy(2000)
        compose.runOnIdle { assertEquals(emptyList<Int>(), completed) }
    }

    @Test
    fun zeroSystemAnimationScalePublishesImmediatelyAfterTheFrontIsDrawn() {
        systemScale.scaleFactor = 0f
        show(CollectionCardDesign.SafeHome)
        compose.onNodeWithText("변화 만나보기").performClick()
        compose.mainClock.advanceTimeBy(128)
        compose.onNodeWithText("컬렉션에서 보기").assertExists()
        compose.runOnIdle { assertEquals(listOf(5), completed) }
    }

    private fun verifyReveal(design: CollectionCardDesign, action: String, motion: CardRevealMotion) {
        val card = show(design)
        if (card.revealedLevel == null) compose.onNodeWithText(design.title).assertDoesNotExist()
        compose.onNodeWithText(action).performClick()
        advanceMotion(motion.durationMillis - 192, motion)
        compose.onNodeWithText(action).assertIsNotEnabled()
        compose.onNodeWithText("컬렉션에서 보기").assertDoesNotExist()
        if (card.revealedLevel == null) compose.onNodeWithText(design.title).assertDoesNotExist()
        compose.runOnIdle { assertEquals(emptyList<Int>(), completed) }

        advanceMotion(352, motion)
        compose.onNodeWithText("컬렉션에서 보기").assertExists()
        compose.mainClock.advanceTimeBy(3000)
        compose.runOnIdle { assertEquals(listOf(card.earnedLevel), completed) }
    }

    private fun show(design: CollectionCardDesign): CollectionCard {
        val initial = CollectionSeed.cards.single { it.design == design }
        var card by mutableStateOf(initial)
        val owner = compose.runOnUiThread {
            object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry(this)
            }.also {
                lifecycle = it.lifecycle
                lifecycle.currentState = Lifecycle.State.RESUMED
            }
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                PleosTheme {
                    CollectionRevealScreen(
                        card = card,
                        onBack = { backPresses++ },
                        onViewCollection = {},
                        onSetRepresentative = {},
                        onLater = {},
                        onComplete = { level ->
                            completed.add(level)
                            card = card.copy(revealedLevel = level)
                            true
                        },
                        canOpen = { allowed },
                        modifier = Modifier.requiredSize(1000.dp, 592.dp),
                    )
                }
            }
        }
        return initial
    }

    private fun advanceMotion(millis: Int, motion: CardRevealMotion) {
        if (InstrumentationRegistry.getArguments().getString("captureRevealMotion") != "true") {
            compose.mainClock.advanceTimeBy(millis.toLong())
            return
        }
        val baseDir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        val directory = File(baseDir, "reveal-motion/" + motion.name).apply { mkdirs() }
        val end = compose.mainClock.currentTime + millis
        while (compose.mainClock.currentTime < end) {
            val bitmap = compose.onNodeWithTag("collection-reveal-stage").captureToImage().asAndroidBitmap()
            val frame = (savedFrames++).toString().padStart(3, '0')
            File(directory, frame + ".png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            compose.mainClock.advanceTimeBy(minOf(32L, end - compose.mainClock.currentTime))
        }
    }
}
