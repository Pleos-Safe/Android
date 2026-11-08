package io.issac.pleosandroid.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.navigation.BadgesRoute
import io.issac.pleosandroid.core.navigation.EnhancementRoute
import io.issac.pleosandroid.core.navigation.ExploreRoute
import io.issac.pleosandroid.core.navigation.MainRoute
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.core.navigation.NavEntryResult
import io.issac.pleosandroid.core.navigation.RecordsRoute
import io.issac.pleosandroid.core.navigation.SettingsRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainTabsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun tabMotionFollowsOrderAndRestrictionInterruptsDetailMotion() {
        var restricted by mutableStateOf(false)
        val factories = setOf(
            NavEntryFactory { route ->
                NavEntryResult { Box(Modifier.fillMaxSize().testTag(route.toString())) }
            },
        )
        compose.setContent {
            PleosTheme {
                MainTabs(factories, restricted, { !restricted }, Modifier.requiredWidth(400.dp)) {
                    Text("조작 제한 본문")
                }
            }
        }
        val origin = compose.onNodeWithTag(MainRoute.toString()).getUnclippedBoundsInRoot().left
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("기록").performClick()
        compose.mainClock.advanceTimeBy(96)
        val forward = compose.onNodeWithTag(RecordsRoute.toString()).getUnclippedBoundsInRoot().left - origin
        assertTrue("Incoming tab offset: $forward", forward > 0.dp && forward <= 400.dp / 12)

        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("main-tab-Travel").performClick()
        compose.mainClock.advanceTimeBy(96)
        val reverse = compose.onNodeWithTag(MainRoute.toString()).getUnclippedBoundsInRoot().left - origin
        assertTrue("Returning tab offset: $reverse", reverse < 0.dp && reverse >= -400.dp / 12)

        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("설정").performClick()
        compose.mainClock.advanceTimeBy(96)
        val detail = compose.onNodeWithTag(SettingsRoute.toString()).getUnclippedBoundsInRoot().left - origin
        assertTrue("Incoming detail offset: $detail", detail > forward)
        compose.runOnUiThread { restricted = true }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("조작 제한 본문").assertExists()
        compose.onNodeWithTag(SettingsRoute.toString()).assertDoesNotExist()
        compose.onNodeWithTag(MainRoute.toString()).assertDoesNotExist()
    }

    @Test
    fun tabAndDetailStateSurviveResizeRestrictionAndSavedStateRestore() {
        var restricted by mutableStateOf(false)
        var width by mutableStateOf(1216.dp)
        var latestVehicleAllowsTabs = true
        val factories = setOf(
            NavEntryFactory { route ->
                when (route) {
                    MainRoute -> NavEntryResult { Text("여행 본문") }

                    ExploreRoute -> NavEntryResult { Text("탐색 본문") }

                    EnhancementRoute -> NavEntryResult { Text("강화 본문") }

                    BadgesRoute -> NavEntryResult { Text("컬렉션 본문") }

                    RecordsRoute -> NavEntryResult { Text("기록 본문") }

                    SettingsRoute -> NavEntryResult {
                        var value by rememberSaveable { mutableIntStateOf(0) }
                        Button(onClick = { value++ }) { Text("설정 값 $value") }
                    }

                    else -> null
                }
            },
        )
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            PleosTheme {
                MainTabs(factories, restricted, {
                    latestVehicleAllowsTabs
                }, Modifier.requiredWidth(width).testTag("main")) {
                    Text("주행 상태")
                }
            }
        }

        val fullTab = compose.onNodeWithText("기록").getUnclippedBoundsInRoot()
        val travel = compose.onNodeWithText("여행 본문").getUnclippedBoundsInRoot()
        assertTrue("Full tab $fullTab, travel content $travel", fullTab.bottom <= travel.top)

        // A new restriction can reach the repository before the composable gets its next frame.
        compose.runOnIdle { latestVehicleAllowsTabs = false }
        compose.onNodeWithText("기록").performClick()
        compose.onNodeWithText("여행 본문").assertExists()
        compose.onNodeWithContentDescription("설정").performClick()
        compose.onNodeWithText("설정 값 0").assertDoesNotExist()

        compose.runOnIdle { latestVehicleAllowsTabs = true }
        compose.onNodeWithText("기록").performClick()
        compose.onNodeWithContentDescription("설정").performClick()
        compose.onNodeWithText("설정 값 0").performClick()
        compose.runOnIdle { width = 400.dp }
        compose.onAllNodesWithText("기록").assertCountEquals(1)
        compose.onNodeWithText("기록").assertIsSelected()
        compose.onNodeWithText("설정 값 1").assertExists()
        val splitTab = compose.onNodeWithText("기록").getUnclippedBoundsInRoot()
        val frame = compose.onNodeWithTag("main").getUnclippedBoundsInRoot()
        assertEquals("Split tab $splitTab, main frame $frame", frame.bottom, splitTab.bottom)

        compose.onNodeWithTag("main-tab-Explore").performClick()
        compose.onNodeWithText("탐색 본문").assertExists()
        compose.onNodeWithTag("main-tab-Enhance").performClick()
        compose.onNodeWithText("강화 본문").assertExists()
        compose.onNodeWithText("컬렉션").performClick()
        compose.onNodeWithContentDescription("설정").performClick()
        compose.onNodeWithText("설정 값 0").assertExists()
        compose.onNodeWithText("기록").performClick()
        compose.onNodeWithText("설정 값 1").assertExists()
        compose.runOnIdle { restricted = true }
        compose.onNodeWithText("주행 상태").assertExists()
        compose.onNodeWithText("기록").assertDoesNotExist()
        compose.onNodeWithContentDescription("설정").assertDoesNotExist()
        compose.onNodeWithText("설정 값 1").assertDoesNotExist()

        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { restricted = false }
        compose.onNodeWithText("기록").assertIsSelected()
        compose.onNodeWithText("설정 값 1").assertExists()
        compose.onNodeWithText("기록").performClick()
        compose.onNodeWithText("기록 본문").assertExists()
    }
}
