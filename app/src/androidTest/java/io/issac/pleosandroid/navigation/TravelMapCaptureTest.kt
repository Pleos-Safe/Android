package io.issac.pleosandroid.navigation

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.issac.pleosandroid.MainActivity
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt in with `-e pleosMapCapture true` while the Pleos emulator is parked. */
@RunWith(AndroidJUnit4::class)
class TravelMapCaptureTest {
    @get:Rule(order = 0)
    val permission: GrantPermissionRule = GrantPermissionRule.grant(VEHICLE_SPEED_PERMISSION)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun captureTravelMapStates() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("pleosMapCapture") == "true")

        waitForText("지도에서 찾기")
        compose.onNodeWithText("지도에서 찾기").performClick()
        waitForText("지도에서 여행지 찾기")
        waitForText("부산 여행지 · 4곳")
        compose.waitUntil(timeoutMillis = 60_000) { !hasText("지도를 불러오고 있어요.") }
        capture("travel-map-01-default.png")

        compose.onNodeWithText("해운대 해수욕장").performScrollTo().performClick()
        waitForText("부산 해운대구 · 해변")
        compose.onNodeWithText("부산 해운대구 · 해변").performScrollTo()
        capture("travel-map-05-haeundae-setup.png")

        search("수영구")
        waitForText("부산 여행지 · 1곳")
        compose.onNodeWithText("광안리 해수욕장").performScrollTo().performClick()
        waitForText("부산 수영구 · 해변")
        compose.onNodeWithText("부산 수영구 · 해변").performScrollTo()
        capture("travel-map-02-selected.png")

        compose.onNodeWithText("목록으로 보기").performScrollTo().performClick()
        waitForText("지도에서 보기")
        search("")
        waitForText("부산 여행지 · 4곳")
        capture("travel-map-03-list.png")

        search("일치하지 않는 여행지")
        waitForText("이 조건에 맞는 여행지가 없어요.")
        capture("travel-map-04-empty.png")
    }

    private fun search(query: String) {
        compose.onNodeWithContentDescription("장소 검색").performScrollTo().performTextReplacement(query)
        compose.onNodeWithContentDescription("장소 검색").performImeAction()
    }

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
}

private const val VEHICLE_SPEED_PERMISSION = "android.car.permission.CAR_SPEED"
