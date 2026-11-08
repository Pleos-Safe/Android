package io.issac.pleosandroid.navigation

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText as matchesText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.issac.pleosandroid.MainActivity
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/** Opt in only on an isolated, parked Pleos emulator with no destination set. */
@RunWith(AndroidJUnit4::class)
class PleosRouteAcceptanceTest {
    @get:Rule(order = 0)
    val isolatedEmulator: TestRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val arguments = InstrumentationRegistry.getArguments()
                assumeTrue(
                    "Route acceptance requires explicit opt-in.",
                    arguments.getString("pleosRouteAcceptance") == "true",
                )
                assumeTrue("Route acceptance is limited to a ranchu emulator.", Build.HARDWARE == "ranchu")
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 1)
    val permission: GrantPermissionRule = GrantPermissionRule.grant(VEHICLE_SPEED_PERMISSION)

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun setsDestinationAndAddsWaypointThroughPleosNavigation() {
        openMap()
        selectPlace("해운대 해변")
        captureBeforeAction(SET_DESTINATION, ADD_WAYPOINT, "route-01-before-destination.png")
        compose.onNodeWithText(SET_DESTINATION).performClick()
        assertApplied("route-02-destination")

        // Re-enter so the waypoint request starts from a fresh read of the confirmed destination.
        compose.onNodeWithText("여행 홈으로").performScrollTo().performClick()
        openMap()
        selectPlace("광안리 해변")
        captureBeforeAction(ADD_WAYPOINT, SET_DESTINATION, "route-03-before-waypoint.png")
        compose.onNodeWithText(ADD_WAYPOINT).performClick()
        waitForText("가는 길에 들를까요?")
        capture("route-04-confirm-waypoint.png")
        compose.onNode(matchesText(ADD_WAYPOINT) and hasAnyAncestor(isDialog())).performClick()
        assertApplied("route-05-waypoint")
    }

    private fun openMap() {
        waitForText("지도에서 찾기")
        compose.onNodeWithText("지도에서 찾기").performClick()
        waitForText("지도에서 여행지 찾기")
        waitForText("여행지 · 9곳")
    }

    private fun selectPlace(name: String) {
        waitForText(name)
        compose.onNodeWithText(name).performScrollTo().performClick()
    }

    private fun captureBeforeAction(action: String, unexpectedAction: String, filename: String) {
        try {
            compose.waitUntil(timeoutMillis = 25_000) {
                hasEnabledAction(action) ||
                    hasEnabledAction(unexpectedAction) ||
                    hasText(RouteOutcome.Unavailable.label)
            }
            val visibleAction = if (hasText(action)) action else unexpectedAction
            if (hasText(visibleAction)) compose.onNodeWithText(visibleAction).performScrollTo()
        } finally {
            capture(filename)
        }
        compose.onNodeWithText(action).assertIsEnabled()
    }

    private fun assertApplied(filenameStem: String) {
        var outcome: RouteOutcome? = null
        try {
            compose.waitUntil(timeoutMillis = 45_000) {
                outcome = RouteOutcome.entries.firstOrNull { hasText(it.label) }
                outcome != null
            }
            outcome?.let { compose.onNodeWithText(it.label).performScrollTo() }
        } finally {
            capture("$filenameStem-${outcome?.fileTag ?: "timed-out"}.png")
        }
        check(outcome == RouteOutcome.Applied) { "Pleos navigation did not apply $filenameStem: $outcome" }
    }

    private fun hasEnabledAction(action: String): Boolean =
        runCatching { compose.onNodeWithText(action).assertIsEnabled() }.isSuccess

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 15_000) { hasText(text) }
    }

    private fun hasText(text: String): Boolean =
        compose.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    private fun capture(filename: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.filesDir, "screenshots")
        check(output.isDirectory || output.mkdirs())
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(output, filename).outputStream().use { stream ->
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally {
            screenshot.recycle()
        }
    }

    private enum class RouteOutcome(val label: String, val fileTag: String) {
        Applied("내비게이션에서 경로 변경을 확인했어요.", "applied"),
        Rejected("경로 변경이 거절됐어요. 기존 내비 상태를 유지해요.", "rejected"),
        Unconfirmed(
            "내비게이션의 응답을 받지 못했어요. 현재 경로를 먼저 확인해 주세요.",
            "unconfirmed",
        ),
        Unavailable("내비게이션에 연결할 수 없어요.", "unavailable"),
    }
}

private const val VEHICLE_SPEED_PERMISSION = "android.car.permission.CAR_SPEED"
private const val SET_DESTINATION = "목적지로 설정"
private const val ADD_WAYPOINT = "경유지로 추가"
