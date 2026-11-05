package io.issac.pleosandroid.navigation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import io.issac.pleosandroid.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run with the Pleos emulator parked, without changing any vehicle properties. */
@RunWith(AndroidJUnit4::class)
class MainActivityFlowTest {
    @get:Rule(order = 0)
    val permission: GrantPermissionRule = GrantPermissionRule.grant(VehicleSpeedPermission)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun hiltFactoriesObserveVehicleFlowAndRestoreJourneyAcrossCollectionAndRecreation() {
        waitFor("주행 전")
        compose.waitUntil(timeoutMillis = 15_000) {
            runCatching { compose.onNodeWithText("여정 시작").assertIsEnabled() }.isSuccess
        }
        compose.onNodeWithText("여정 시작").performClick()
        waitFor("주행 중 · 멈춤")
        compose.onNodeWithText("컬렉션 보기").performClick()
        waitFor("나의 컬렉션")
        compose.onNodeWithText("뒤로").performClick()
        waitFor("주행 중 · 멈춤")
        compose.onNodeWithText("여정 종료").performClick()
        waitFor("주행 종료")

        compose.activityRule.scenario.recreate()

        waitFor("주행 종료")
    }

    @Test
    fun collectionFiltersCardsAndRestoresSelectedCategory() {
        compose.waitUntil(timeoutMillis = 15_000) {
            runCatching { compose.onNodeWithText("컬렉션 보기").assertIsEnabled() }.isSuccess
        }
        compose.onNodeWithText("컬렉션 보기").performClick()
        waitFor("나의 컬렉션")
        waitFor("해운대의 파도")
        waitFor("안전한 귀가")

        compose.onNodeWithText("장소").performClick()
        compose.onAllNodesWithText("안전한 귀가").assertCountEquals(0)

        compose.activityRule.scenario.recreate()

        waitFor("나의 컬렉션")
        waitFor("해운대의 파도")
        compose.onAllNodesWithText("안전한 귀가").assertCountEquals(0)

        compose.onNodeWithText("운전습관").performClick()
        waitFor("안전한 귀가")
        compose.onNodeWithText("전체").performClick()
        waitFor("해운대의 파도")
        waitFor("안전한 귀가")
    }

    private fun waitFor(text: String) {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

private const val VehicleSpeedPermission = "android.car.permission.CAR_SPEED"
