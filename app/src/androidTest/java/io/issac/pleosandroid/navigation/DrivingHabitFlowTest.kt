package io.issac.pleosandroid.navigation

import android.app.ActivityManager
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.EntryPointAccessors
import io.issac.pleosandroid.MainActivity
import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.model.DeleteHistoryRequest
import io.issac.pleosandroid.core.domain.collection.model.HistoryKind
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.collection.model.ProgressEvent
import io.issac.pleosandroid.core.domain.collection.model.RecordProgressResult
import io.issac.pleosandroid.core.domain.driving.model.DrivingAssessment
import io.issac.pleosandroid.core.domain.driving.repository.RecordingPhase
import io.issac.pleosandroid.debug.CollectionDebugEntryPoint
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DrivingHabitFlowTest {
    @get:Rule(order = 0)
    val permission: GrantPermissionRule = GrantPermissionRule.grant(
        "android.car.permission.CAR_SPEED",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.POST_NOTIFICATIONS",
    )

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()
    private val entry get() = EntryPointAccessors.fromApplication(
        compose.activity.applicationContext,
        CollectionDebugEntryPoint::class.java,
    )
    private var generation: String? = null
    private var previousDemo: Boolean? = null
    private var previousEnabled: Boolean? = null

    @After fun restore() = runBlocking<Unit> {
        generation?.let { entry.progress().deleteHistory(DeleteHistoryRequest(HistoryKind.Journey, ID, it)) }
        previousEnabled?.let { entry.recorder().setEnabled(it) }
        previousDemo?.let { entry.demo().setEnabled(it) }
    }

    @Test fun savedReportSurvivesRecreationAndOpensSafetyCollection() {
        runBlocking {
            previousDemo = entry.demo().enabled.first()
            entry.demo().setEnabled(true)
            generation = entry.progress().getDataGeneration()
            val record = JourneyRecord(
                ID, Instant.parse("2026-11-06T10:00:00Z"), Instant.parse("2026-11-06T10:10:00Z"),
                "Asia/Seoul", 5000, AssessmentStatus.Complete, 88, 0,
                driving = DrivingAssessment(
                    elapsedMillis = 600_000,
                    observedMillis = 599_000,
                    movingMillis = 599_000,
                    sampleCount = 3000,
                    rejectedSamples = 0,
                    harshAccelerations = 2,
                    harshBrakings = 1,
                    harshStarts = 0,
                    longestGapMillis = 200,
                ),
            )
            val result = entry.progress().recordProgress(ProgressEvent.JourneyCompleted(ID, generation!!, record, true))
            assertTrue(result is RecordProgressResult.Applied)
            (result as RecordProgressResult.Applied).receipt.noticeIds.forEach {
                entry.cards().acknowledgeRewardNotice(it)
            }
        }
        waitFor("기록")
        compose.onNodeWithText("기록").performClick()
        waitFor("운전습관 보기")
        compose.onNodeWithText("운전습관 보기").performScrollTo().performClick()
        waitFor("이번 여정의 운전습관")
        waitFor("88")
        compose.activityRule.scenario.recreate()
        waitFor("88")
        compose.onNodeWithText("데이터 상태").performScrollTo().performClick()
        waitFor("운전습관 기록 상태")
        compose.onNodeWithText("리포트 보기").performScrollTo().performClick()
        waitFor("이번 여정의 운전습관")
        compose.onNodeWithText("안전운전 컬렉션").performScrollTo().performClick()
        waitFor("나의 컬렉션")
        compose.onNodeWithText("운전습관").assertIsOn()
        compose.onNodeWithText("기록").performClick()
        waitFor("이번 여정의 운전습관")
    }

    @Test fun optInStartsForegroundRecordingAndConstantSpeedHasFreshTimestamps() {
        runBlocking {
            previousDemo = entry.demo().enabled.first()
            previousEnabled = entry.recorder().enabled.value
            entry.demo().setEnabled(false)
            entry.recorder().setEnabled(false)
        }
        waitFor("기록")
        compose.onNodeWithContentDescription("설정").performClick()
        waitFor("여정 기록 저장")
        compose.onNodeWithText("여정 기록 저장").performClick()
        waitFor("나의 기록 방식")
        compose.waitUntil(15_000) {
            runCatching { compose.onNodeWithContentDescription("여정 기록 저장").assertIsEnabled() }.isSuccess &&
                entry.vehicle().state.value.allowsTabNavigation
        }
        compose.onNodeWithContentDescription("여정 기록 저장").performClick()
        compose.waitUntil(15_000) { entry.recorder().enabled.value }
        runBlocking {
            withTimeout(15_000) { entry.recorder().state.first { it.phase == RecordingPhase.Waiting } }
            assertTrue(entry.recorder().enabled.value)
            val first = withTimeout(15_000) {
                entry.vehicle().state.first { it.motion.timestampNanos != null }
            }.motion.timestampNanos!!
            withTimeout(5_000) { entry.vehicle().state.first { (it.motion.timestampNanos ?: 0) > first } }
        }
        val context = compose.activity.applicationContext
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        @Suppress("DEPRECATION")
        val service = context.getSystemService(ActivityManager::class.java).getRunningServices(100)
            .firstOrNull { it.service.className.endsWith("DrivingRecordingService") }
        assertTrue(service?.foreground == true)
        assertEquals(RecordingPhase.Waiting, entry.recorder().state.value.phase)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    }

    private fun waitFor(text: String) {
        compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val ID = "driving-ui-test"
    }
}
