package io.issac.pleosandroid.navigation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
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
    val permission: GrantPermissionRule = GrantPermissionRule.grant(VEHICLE_SPEED_PERMISSION)

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
        openCollection()
        compose.onNodeWithText("뒤로").performClick()
        waitFor("주행 중 · 멈춤")
        compose.onNodeWithText("여정 종료").performClick()
        waitFor("주행 종료")

        compose.activityRule.scenario.recreate()

        waitFor("주행 종료")
    }

    @Test
    fun collectionFiltersCardsAndRestoresSelectedCategory() {
        openCollection()
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

    @Test
    fun collectionOpensSelectedCardAndRestoresDetailAndCategory() {
        openCollection()
        compose.onNodeWithText("장소").performClick()
        openCard("place-haeundae-001")
        waitFor("처음 간직한 날")
        waitFor("11월 2일 · 가장 최근에 남긴 기억")

        compose.activityRule.scenario.recreate()

        waitFor("11월 2일 · 가장 최근에 남긴 기억")
        waitFor("해운대의 파도")
        compose.onNodeWithText("뒤로").performClick()
        waitFor("나의 컬렉션")
        waitFor("해운대의 파도")
        compose.onAllNodesWithText("안전한 귀가").assertCountEquals(0)

        compose.onNodeWithText("운전습관").performClick()
        openCard("safety-smooth-001")
        waitFor("운전습관의 기억")
        waitFor("11월 2일 · 가장 최근에 남긴 여정")
    }

    private fun openCollection() {
        compose.waitUntil(timeoutMillis = 15_000) {
            runCatching { compose.onNodeWithText("컬렉션 보기").assertIsEnabled() }.isSuccess
        }
        compose.onNodeWithText("컬렉션 보기").performClick()
        waitFor("나의 컬렉션")
    }

    private fun openCard(cardId: String) {
        compose.onNodeWithTag("collection-card-$cardId").performClick()
    }

    private fun waitFor(text: String) {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

private const val VEHICLE_SPEED_PERMISSION = "android.car.permission.CAR_SPEED"
