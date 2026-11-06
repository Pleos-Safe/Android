package io.issac.pleosandroid.navigation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.EntryPointAccessors
import io.issac.pleosandroid.MainActivity
import io.issac.pleosandroid.debug.CollectionDebugEntryPoint
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run with the Pleos emulator parked, without changing any vehicle properties. */
@RunWith(AndroidJUnit4::class)
class MainActivityFlowTest {
    @Before
    fun prepareIsolatedDemoDatabase() = runBlocking {
        val entryPoint = EntryPointAccessors.fromApplication(
            compose.activity.applicationContext,
            CollectionDebugEntryPoint::class.java,
        )
        entryPoint.demo().setEnabled(true)
        entryPoint.demo().resetDemo()
    }

    @get:Rule(order = 0)
    val permission: GrantPermissionRule = GrantPermissionRule.grant(VEHICLE_SPEED_PERMISSION)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun tabsKeepTheirDestinationAcrossSwitchesAndActivityRecreation() {
        waitFor("기록")
        compose.onNodeWithText("기록").performClick()
        waitFor("여정 기록")
        compose.onNodeWithContentDescription("설정").performClick()
        waitFor("돌아가기")
        compose.onNodeWithText("컬렉션").performClick()
        waitFor("나의 컬렉션")
        compose.onNodeWithText("기록").performClick()
        waitFor("돌아가기")

        compose.activityRule.scenario.recreate()
        waitFor("돌아가기")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitFor("여정 기록")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitFor("주행 전")
    }

    @Test
    fun parkedVehicleStaysBeforeDrivingAcrossCollectionAndActivityRecreation() {
        waitFor("주행 전")
        compose.onNodeWithText("여정 시작").assertDoesNotExist()
        openCollection()
        systemBack()
        waitFor("주행 전")

        compose.activityRule.scenario.recreate()

        waitFor("주행 전")
        compose.onNodeWithText("여정 시작").assertDoesNotExist()
        compose.onNodeWithText("주행 종료").assertDoesNotExist()
        compose.onNodeWithText("홈으로").assertDoesNotExist()
    }

    @Test
    fun travelMapFiltersTheBundledCatalogAndReturnsHome() {
        waitFor("지도에서 찾기")
        compose.onNodeWithText("지도에서 찾기").performClick()
        waitFor("지도에서 여행지 찾기")
        waitFor("여행지 · 6곳")

        compose.onNodeWithContentDescription("장소 검색").performTextInput("수영구")
        waitFor("여행지 · 1곳")
        compose.onNodeWithText("해운대 해수욕장").assertDoesNotExist()

        compose.onNodeWithText("여행 홈으로").performClick()
        waitFor("주행 전")
    }

    @Test
    fun collectionFiltersCardsAndRestoresSelectedCategory() {
        openCollection()
        waitFor("해운대의 파도")
        waitFor("광안대교의 밤")

        compose.onNodeWithText("장소").performClick()
        compose.onAllNodesWithText("안전한 귀가").assertCountEquals(0)

        compose.activityRule.scenario.recreate()

        waitFor("나의 컬렉션")
        compose.onNodeWithText("장소").assertIsOn()
        waitFor("해운대의 파도")
        compose.onAllNodesWithText("안전한 귀가").assertCountEquals(0)

        compose.onNodeWithText("운전습관").performClick()
        waitFor("안전한 귀가")
        compose.onNodeWithText("전체").performClick()
        waitFor("해운대의 파도")
        waitFor("광안대교의 밤")
    }

    @Test
    fun collectionOpensSelectedCardAndRestoresDetailAndCategory() {
        openCollection()
        compose.onNodeWithText("장소").performClick()
        openCard("place-gwangalli-001")
        waitFor("처음 간직한 날")
        waitFor("10월 31일 · 가장 최근에 남긴 기억")

        compose.activityRule.scenario.recreate()

        waitFor("10월 31일 · 가장 최근에 남긴 기억")
        waitFor("광안대교의 밤")
        compose.onNodeWithContentDescription("뒤로").performClick()
        waitFor("나의 컬렉션")
        waitFor("해운대의 파도")
        compose.onAllNodesWithText("안전한 귀가").assertCountEquals(0)

        compose.onNodeWithText("운전습관").performClick()
        openCard("safety-smooth-001")
        waitFor("변화 만나보기")
        compose.onNodeWithText("변화 만나보기").performClick()
        waitFor("컬렉션에서 보기")
        compose.onNodeWithText("컬렉션에서 보기").performClick()
        waitFor("나의 컬렉션")
        compose.onNodeWithText("운전습관").assertIsOn()
        openCard("safety-smooth-001")
        waitFor("운전습관의 기억")
        waitFor("10월 30일 · 가장 최근에 남긴 여정")
    }

    @Test
    fun revealResultReturnsToTheCardsCategoryAndPage() {
        openCollection()
        compose.onNodeWithText("장소").performClick()
        compose.onNodeWithText("필터·정렬").performClick()
        compose.onNodeWithText("해운대구").performClick()
        compose.onNodeWithText("카드 2장 보기").performClick()
        compose.onNodeWithText("운전습관").performClick()
        compose.onNodeWithText("열어볼 5장").performClick()
        waitFor("아직 열지 않은 기억")
        compose.onNodeWithText("카드 열기").performClick()
        waitFor("송도 해수욕장의 한 장이 도착했어요")
        compose.onNodeWithContentDescription("뒤로").performClick()
        waitFor("5장의 카드가 기다려요")
        compose.onNodeWithText("카드 열기").performClick()
        waitFor("나중에 열기")
        compose.onNodeWithText("카드 열기").performClick()
        waitFor("컬렉션에서 보기")
        compose.onNodeWithText("컬렉션에서 보기").performClick()
        waitFor("5–6 / 6")
        compose.onNodeWithText("장소").assertIsOn()
        compose.onNodeWithTag("collection-card-place-songdo-001").assertExists()
        compose.activityRule.scenario.recreate()
        waitFor("5–6 / 6")
        compose.onNodeWithText("이전").performClick()
        compose.onNodeWithTag("collection-card-place-songdo-001").assertDoesNotExist()
        systemBack()
        waitFor("주행 전")
    }

    @Test
    fun filterDraftCanBeCancelledAndAnEmptyResultKeepsOwnership() {
        openCollection()
        compose.onNodeWithText("필터·정렬").performClick()
        compose.onNodeWithText("전망·명소").performClick()
        waitFor("카드 3장 보기")
        compose.onNodeWithText("닫기").performClick()
        waitFor("해운대의 파도")
        compose.onNodeWithText("필터·정렬").performClick()
        waitFor("카드 8장 보기")
        compose.onNodeWithText("영도구").performClick()
        compose.onNodeWithText("전망·명소").performClick()
        compose.onNodeWithText("카드 0장 보기").performClick()
        waitFor("아직 이 조건의 카드는 없어요")
        waitFor("간직한 카드 8장")
        compose.onNodeWithText("지역·유형 초기화").performClick()
        waitFor("광안대교의 밤")
    }

    @Test
    fun unopenedCardKeepsItsIdentityHiddenUntilOpenedAndCanThenBeDisplayed() {
        openCollection()
        compose.onNodeWithText("열어볼 5장").performClick()
        waitFor("아직 열지 않은 기억")
        compose.onNodeWithText("카드 열기").performClick()
        waitFor("송도 해수욕장의 한 장이 도착했어요")
        compose.onAllNodesWithText("송도의 푸른 길").assertCountEquals(0)
        compose.onNodeWithText("나중에 열기").performClick()
        waitFor("5장의 카드가 기다려요")
        compose.onNodeWithText("카드 열기").performClick()
        waitFor("나중에 열기")
        compose.onNodeWithText("카드 열기").performClick()
        waitFor("송도의 푸른 길")
        compose.onNodeWithText("대표 카드로 설정").performClick()
        waitFor("이 카드로 전시하기")
        compose.onNodeWithText("이 카드로 전시하기").performClick()
        waitFor("나의 대표 카드")
        waitFor("송도의 푸른 길")
        compose.activityRule.scenario.recreate()
        waitFor("송도의 푸른 길")
        openCollection()
        waitFor("열어볼 4장")
        waitFor("간직한 카드 8장")
        compose.onNodeWithText("열어볼 4장").performClick()
        compose.onNodeWithText("시연 카드 다시 준비").performScrollTo().performClick()
        waitFor("5장의 카드가 기다려요")
        for (level in 1..4) {
            compose.onNodeWithText("Lv.$level → Lv.${level + 1} 진화 대기").assertExists()
        }
        waitFor("미개봉")
    }

    private fun systemBack() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
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
            compose.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }
}

private const val VEHICLE_SPEED_PERMISSION = "android.car.permission.CAR_SPEED"
