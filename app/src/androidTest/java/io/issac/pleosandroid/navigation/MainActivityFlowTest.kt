package io.issac.pleosandroid.navigation

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.issac.pleosandroid.MainActivity
import io.issac.pleosandroid.core.database.CollectionDatabase
import io.issac.pleosandroid.core.database.entity.CopyEntity
import io.issac.pleosandroid.core.database.entity.PackEntity
import io.issac.pleosandroid.core.domain.cards.CardGrade
import io.issac.pleosandroid.core.domain.cards.CardRules
import io.issac.pleosandroid.core.domain.cards.CopyStatus
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.testing.CollectionTestEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run with the Pleos emulator parked, without changing any vehicle properties. */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class MainActivityFlowTest {
    @get:Rule(order = -1)
    val hilt = HiltAndroidRule(this)

    private val graph get() = EntryPointAccessors.fromApplication(
        ApplicationProvider.getApplicationContext<Context>(),
        CollectionTestEntryPoint::class.java,
    )

    @Inject lateinit var database: CollectionDatabase

    @Before fun inject() = hilt.inject()

    @After fun closeDatabase() {
        compose.activityRule.scenario.close()
        database.close()
    }

    @get:Rule(order = 0)
    val permission: GrantPermissionRule = GrantPermissionRule.grant(VEHICLE_SPEED_PERMISSION)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun plusPurchaseSurvivesRecreationAndRenewalIsManagedFromSettings() {
        waitTag("main-tab-Shop")
        compose.onNodeWithTag("main-tab-Shop").performClick()
        compose.onNodeWithTag("main-tab-Shop").assertIsSelected()
        waitFor("PLUS 구독하기")
        compose.onNodeWithText("PLUS 구독하기").performScrollTo().performClick()
        compose.onNodeWithText("구독 시작").performClick()
        waitFor("구독 중")
        compose.activityRule.scenario.recreate()
        waitFor("구독 중")
        compose.onNodeWithContentDescription("설정").performClick()
        compose.onNodeWithText("PLUS 구독 관리").performScrollTo().performClick()
        waitFor("구독 해지")
        compose.onNodeWithText("구독 해지").performScrollTo().performClick()
        compose.onNode(hasText("구독 해지") and hasAnyAncestor(isDialog())).performClick()
        waitFor("해지 예약됨")
        compose.activityRule.scenario.recreate()
        waitFor("해지 예약됨")
        compose.onNodeWithText("자동 갱신 재개").performScrollTo().performClick()
        compose.onNode(hasText("자동 갱신 재개") and hasAnyAncestor(isDialog())).performClick()
        waitFor("구독 중")
    }

    @Test
    fun tabsKeepTheirDestinationAcrossSwitchesAndActivityRecreation() {
        waitFor("기록")
        compose.onNodeWithText("기록").performClick()
        waitFor("주행 기록")
        compose.onNodeWithContentDescription("설정").performClick()
        waitFor("돌아가기")
        compose.onNodeWithTag("main-tab-Collection").performClick()
        waitTag("album-open-inbox")
        compose.onNodeWithText("기록").performClick()
        waitFor("돌아가기")

        compose.activityRule.scenario.recreate()
        waitFor("돌아가기")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitFor("주행 기록")
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
        waitFor("여행지 · 9곳")

        compose.onNodeWithContentDescription("장소 검색").performTextInput("수영구")
        waitFor("여행지 · 1곳")
        compose.onNodeWithText("해운대 해수욕장").assertDoesNotExist()

        compose.onNodeWithText("여행 홈으로").performClick()
        waitFor("주행 전")
    }

    @Test fun regionFilterAndCardDetailsSurviveRecreation() {
        val card = runBlocking {
            val album = graph.album()
            val id = album.open(album.receivePack("test-busan", PackType.Busan, "방문 보상"))
            album.reveal(id)
            album.observe().first().collection.single()
        }
        openCollection()
        compose.onNodeWithText("제주").performClick()
        waitFor("이 지역에서 모은 카드가 없어요.")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("제주").assertIsOn()
        compose.onNodeWithText("부산").performClick()
        openCard(card.id)
        waitFor("카드 상세")
        compose.activityRule.scenario.recreate()
        waitFor("소장 번호  " + card.id.takeLast(8))
        systemBack()
        compose.onNodeWithText("부산").assertIsOn()
    }

    @Test fun openedPackHidesItsFrontUntilRevealedAndCanResumeAfterLeaving() {
        waitTag("home-pack-shelf")
        compose.onNodeWithTag("home-pack-shelf").performScrollTo().performClick()
        openWelcome()
        waitEnabled("album-open-seal")
        compose.onNodeWithTag("album-flip").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        waitEnabled("album-open-seal")
        systemBack()
        openWelcome()
        waitEnabled("album-open-seal")
        assertTrue(runBlocking { graph.album().observe().first().byPack["welcome:seoul"] == null })
        compose.onNodeWithTag("album-open-seal").performScrollTo().performClick()
        waitEnabled("album-flip")
        val drawn = runBlocking { graph.album().observe().first().byPack.getValue("welcome:seoul") }
        assertFalse(drawn.seen)
        compose.onAllNodesWithText(drawn.print.title).assertCountEquals(0)
        compose.onNodeWithText("나중에 확인").performScrollTo().performClick()
        openWelcome()
        compose.activityRule.scenario.recreate()
        waitEnabled("album-flip")
        compose.onNodeWithTag("album-open-seal").assertDoesNotExist()
        val resumed = runBlocking { graph.album().observe().first() }
        assertEquals(drawn.id, resumed.copies.single { it.pack.id == "welcome:seoul" }.id)
        assertFalse(resumed.byPack.getValue("welcome:seoul").seen)
        compose.onNodeWithTag("album-flip").performScrollTo().performClick()
        waitTag("album-show-copy")
        compose.onNodeWithTag("album-show-copy").performScrollTo().performClick()
        waitTag("album-copy-" + drawn.id)
        openCard(drawn.id)
        compose.onNodeWithText("대표 카드로 설정").performScrollTo().performClick()
        waitFor("대표 카드로 설정됨")
        compose.onNodeWithTag("main-tab-Travel").performClick()
        waitFor("나의 대표 카드")
        assertEquals(drawn.id, runBlocking { graph.album().observe().first().featuredId })
    }

    @Test fun enhancementFromCollectionPersistsReceiptAndDebitsOnlyOnce() {
        // The test starts at an already received pack, through the same public contract as a future reward provider.
        val card = runBlocking {
            val album = graph.album()
            val id = album.open(album.receivePack("test-visit", PackType.Jeju, "방문 보상"))
            album.reveal(id)
            album.observe().first().collection.single { it.id == id }
        }
        openCollection()
        openCard(card.id)
        waitTag("album-enhance")
        compose.onNodeWithTag("album-enhance").performScrollTo().performClick()
        waitEnabled("forge-attempt")
        val balance = runBlocking { graph.album().observe().first().balanceHalfPoints }
        val attempt = compose.onNodeWithTag("forge-attempt").performScrollTo()
        compose.mainClock.autoAdvance = false
        attempt.performClick()
        compose.waitUntil(15_000) {
            compose.mainClock.advanceTimeBy(32)
            compose.onAllNodes(
                hasTestTag("forge-theatre"),
            ).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.mainClock.advanceTimeBy(6500)
        compose.mainClock.autoAdvance = true
        waitTag("forge-next")

        systemBack()
        waitTag("album-grid")
        val cardTag = "album-copy-" + card.id
        compose.onNodeWithTag("album-grid").performScrollToNode(hasTestTag(cardTag))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(cardTag).performClick()
        compose.waitUntil(15_000) {
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithTag("forge-theatre").assertDoesNotExist()
            compose.onAllNodes(
                hasTestTag("forge-next"),
            ).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.mainClock.autoAdvance = true
        compose.activityRule.scenario.recreate()
        waitTag("forge-next")
        val after = runBlocking { graph.album().observe().first() }
        assertEquals(balance - CardRules.cost(card.grade, 0), after.balanceHalfPoints)
        assertEquals(1, after.history.count { it.copyId == card.id })
        compose.onNodeWithTag("forge-next").performScrollTo().performClick()
        waitEnabled("forge-attempt")
        assertTrue(runBlocking { graph.album().observe().first().pending[card.id] == null })
    }

    @Test fun aTraceIsRestoredWithOnlyTheSelectedMatchingCopy() {
        runBlocking {
            graph.album().observe().first()
            database.withTransaction {
                val dao = database.albumDao()
                val now = System.currentTimeMillis()
                for (status in listOf(CopyStatus.Trace, CopyStatus.Owned)) {
                    val trace = status == CopyStatus.Trace
                    val id = if (trace) "test-trace" else "test-material"
                    val packId = "pack-$id"
                    dao.grant(PackEntity(packId, PackType.Busan.name, "방문 보상", now))
                    dao.add(
                        CopyEntity(
                            id = id, packId = packId, printId = "busan-gwangalli", variant = false,
                            level = if (trace) 7 else 0, highest = if (trace) 12 else 0, status = status.name,
                            revision = 0, acquiredAt = now, seenAt = now,
                        ),
                    )
                }
            }
        }
        openCollection()
        compose.onNodeWithText("흔적 1").performClick()
        openCard("test-trace")
        waitFor("흔적 복구")
        val before = runBlocking { graph.album().observe().first() }
        compose.onNodeWithText("복구 재료 선택 · 1장").performScrollTo().performClick()
        compose.onNode(hasText("광안리의 물결 +0", substring = true)).performClick()
        compose.onNodeWithText("이 사본으로 복구").performClick()
        waitFor("복구 완료")
        val after = runBlocking { graph.album().observe().first() }
        assertEquals(before.balanceHalfPoints, after.balanceHalfPoints)
        assertEquals(ForgeOutcome.Restored, after.pending.getValue("test-trace").outcome)
        assertEquals(7, after.collection.single { it.id == "test-trace" }.level)
        assertEquals(CopyStatus.Spent, after.copies.single { it.id == "test-material" }.status)
        compose.onNodeWithText("컬렉션에서 보기").performScrollTo().performClick()
        waitTag("album-copy-test-trace")
        compose.onNodeWithTag("album-copy-test-trace").assertIsDisplayed()
        waitFor("방금 확인한 카드")
        compose.onNodeWithTag("album-grid").performScrollToNode(hasText("부산"))
        compose.onNodeWithText("부산").assertIsOn()
        assertFalse(runBlocking { "test-trace" in graph.album().observe().first().pending })
    }

    @Test fun hyundaiVisitPackRevealsASpecialGradeCard() {
        val packId = runBlocking { graph.album().receivePack("test-goyang", PackType.Hyundai, "방문 보상") }
        openCollection()
        compose.onNodeWithTag("album-open-inbox").performClick()
        val tag = "album-pack-$packId"
        compose.onNodeWithTag("pack-shelf-grid").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
        waitEnabled("album-open-seal")
        compose.onNodeWithTag("album-open-seal").performScrollTo().performClick()
        waitEnabled("album-flip")
        compose.onNodeWithTag("album-flip").performScrollTo().performClick()
        waitTag("album-show-copy")
        val card = runBlocking { graph.album().observe().first().byPack.getValue(packId) }
        assertFalse(card.variant)
        assertTrue(card.seen)
        assertTrue(card.grade == CardGrade.H || card.grade == CardGrade.HH)
    }

    @Test fun packOddsRemainAccessibleAndBackKeepsTheInventory() {
        openCollection()
        compose.onNodeWithTag("album-open-inbox").performClick()
        waitTag("pack-shelf-grid")
        compose.onNodeWithTag("pack-shelf-grid").performScrollToNode(hasText("카드팩 확률 보기"))
        compose.onNodeWithText("카드팩 확률 보기").performClick()
        waitFor("서울 컬렉션 팩")
        systemBack()
        waitTag("pack-shelf-grid")
        assertTrue(runBlocking { graph.album().observe().first().byPack["welcome:seoul"] == null })
    }

    private fun openWelcome() {
        waitTag("pack-shelf-grid")
        val tag = "album-pack-welcome:seoul"
        compose.onNodeWithTag("pack-shelf-grid").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }

    private fun systemBack() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    private fun openCollection() {
        waitTag("main-tab-Collection")
        compose.onNodeWithTag("main-tab-Collection").performClick()
        waitTag("album-open-inbox")
    }

    private fun openCard(id: String) {
        val tag = "album-copy-" + id
        compose.onNodeWithTag("album-grid").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }

    private fun waitTag(tag: String) {
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun waitEnabled(tag: String) {
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithTag(tag).assertIsEnabled() }.isSuccess }
    }

    private fun waitFor(text: String) {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }
}

private const val VEHICLE_SPEED_PERMISSION = "android.car.permission.CAR_SPEED"
