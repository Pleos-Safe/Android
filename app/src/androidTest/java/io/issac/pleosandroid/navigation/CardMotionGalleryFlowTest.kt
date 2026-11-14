package io.issac.pleosandroid.navigation

import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.issac.pleosandroid.MainActivity
import io.issac.pleosandroid.testing.CollectionTestEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Exercise the parked app's normal tabs using the existing inventory, including an empty one. */
@HiltAndroidTest
class CardMotionGalleryFlowTest {
    @get:Rule(order = -1)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 0)
    val permission: GrantPermissionRule = GrantPermissionRule.grant("android.car.permission.CAR_SPEED")

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun snapshot() = runBlocking {
        EntryPointAccessors.fromApplication(compose.activity.applicationContext, CollectionTestEntryPoint::class.java)
            .album().observe().first()
    }

    private fun waitTag(tag: String) {
        compose.waitUntil(20_000) {
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun enterFrom(tab: String) {
        waitTag("main-tab-$tab")
        compose.onNodeWithTag("main-tab-$tab").performClick()
        waitTag("open-motion-gallery")
        compose.onNodeWithTag("open-motion-gallery").performClick()
        waitTag("motion-gallery")
    }

    private fun play(title: String) {
        compose.onNodeWithTag("motion-gallery").performScrollToNode(hasText(title))
        compose.onNodeWithText(title).performClick()
        waitTag("motion-replay")
    }

    private fun backToGallery() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitTag("motion-gallery")
    }

    @Test fun packAndFrameMilestoneCanBeReplayedWithoutOpeningOrSavingARealCard() {
        enterFrom("Collection")
        val before = snapshot()
        play("제주 컬렉션 팩")
        val replay = compose.onNodeWithTag("motion-replay").performScrollTo()
        compose.mainClock.autoAdvance = false
        replay.performClick()
        compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithTag("motion-skip").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.mainClock.autoAdvance = true
        waitTag("motion-replay")
        backToGallery()
        compose.onNodeWithText("강화 단계").performClick()
        play("+24 → +25")
        backToGallery()
        compose.onNodeWithText("강화 단계").assertIsOn()
        assertEquals(before, snapshot())
    }

    @Test fun forcedForgeAndTranscendenceSurviveRecreationWithNoPointsOrReceiptsSpent() {
        enterFrom("Enhance")
        val before = snapshot()
        compose.onNodeWithText("강화 결과").assertIsOn()
        compose.onNodeWithText("+1").performClick()
        play("대성공")
        compose.onNodeWithText("+15 → +16 → +17").assertExists()
        compose.activityRule.scenario.recreate()
        waitTag("motion-replay")
        compose.onNodeWithTag("forge-theatre").assertDoesNotExist()
        backToGallery()
        compose.onNodeWithText("시작 강화  +15").assertExists()
        compose.onNodeWithText("강화 단계").performClick()
        play("+27 → +28")
        backToGallery()
        assertEquals(before, snapshot())
    }
}
