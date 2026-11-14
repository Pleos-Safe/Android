package io.issac.pleosandroid.feature.records

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.issac.pleosandroid.core.designsystem.component.PleosBottomTabs
import io.issac.pleosandroid.core.designsystem.component.PleosHeader
import io.issac.pleosandroid.core.designsystem.component.PleosTab
import io.issac.pleosandroid.core.designsystem.component.pleosShellBackground
import io.issac.pleosandroid.core.designsystem.theme.PleosWindowTheme
import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.driving.model.DrivingAssessment
import io.issac.pleosandroid.core.domain.driving.model.InsufficientReason
import io.issac.pleosandroid.core.domain.points.PointTransaction
import io.issac.pleosandroid.core.domain.points.PointTransactionKind
import io.issac.pleosandroid.core.domain.points.PointWallet
import io.issac.pleosandroid.feature.records.points.JourneyPointsCard
import io.issac.pleosandroid.feature.records.points.RecordPoints
import io.issac.pleosandroid.feature.records.report.DrivingDataStatusScreen
import io.issac.pleosandroid.feature.records.report.DrivingReportScreen
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DrivingReportUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pointsReceiptSeparatesNormalPointsAndTheHalfPointPlusBonus() {
        val journey = record()
        val credit = PointTransaction(
            "journey:${journey.id}",
            journey.id,
            PointTransactionKind.JourneyEarned,
            867,
            journey.endedAt,
            membershipBonusHalfPoints = 289,
        )
        val points = RecordPoints.Ready(PointWallet(867, listOf(credit)))
        compose.setContent {
            PleosWindowTheme(Modifier.requiredSize(600.dp, 760.dp)) {
                JourneyPointsCard(journey, points) {}
            }
        }
        compose.onNodeWithText("주행 적립").assertIsDisplayed()
        compose.onNodeWithText("289 P").assertIsDisplayed()
        compose.onNodeWithText("PLUS 추가 적립").assertIsDisplayed()
        compose.onNodeWithText("+144.5 P").assertIsDisplayed()
        compose.onNodeWithText("433.5 P").assertIsDisplayed()
    }

    @Test fun reportAdaptsToFullAndSplitWithoutLosingResultOrActions() {
        var width by mutableStateOf(1682.dp)
        var height by mutableStateOf(1210.dp)
        var dataClicks = 0
        var collectionClicks = 0
        var backClicks = 0
        compose.setContent {
            PleosWindowTheme(Modifier.requiredSize(width, height)) {
                Column(Modifier.fillMaxSize().pleosShellBackground().testTag("report-viewport")) {
                    PleosHeader(PleosTab.Records, {}, {}, false)
                    Box(Modifier.weight(1f)) {
                        DrivingReportScreen(record(), { backClicks++ }, { dataClicks++ }, { collectionClicks++ })
                    }
                    PleosBottomTabs(PleosTab.Records, {}, false)
                }
            }
        }
        compose.onNodeWithText("88").assertIsDisplayed()
        compose.onNodeWithText("측정 상세").performScrollTo().performClick()
        compose.onNodeWithText("카드 컬렉션").performClick()
        capture("driving-report-full")
        compose.runOnIdle { width = 841.dp }
        compose.onNodeWithText("88").performScrollTo().assertIsDisplayed()
        capture("driving-report-split")
        compose.onNodeWithText("측정 상세").performScrollTo().performClick()
        compose.runOnIdle {
            width = 520.dp
            height = 760.dp
        }
        compose.onNodeWithText("카드 컬렉션").performScrollTo().performClick()
        compose.onNodeWithContentDescription("뒤로가기").performScrollTo().performClick()
        assertEquals(2, dataClicks)
        assertEquals(2, collectionClicks)
        assertEquals(1, backClicks)
    }

    @Test fun insufficientTripShowsReasonsWithoutScoreOrPositiveGrade() {
        val insufficient = record().copy(
            assessment = AssessmentStatus.Insufficient,
            score = null,
            driving = record().driving!!.copy(
                reasons = setOf(InsufficientReason.Interrupted, InsufficientReason.LowCoverage),
            ),
        )
        compose.setContent {
            PleosWindowTheme(Modifier.requiredSize(1216.dp, 760.dp)) {
                Box(Modifier.testTag("report-viewport")) { DrivingReportScreen(insufficient, {}, {}, {}) }
            }
        }
        compose.onNodeWithText("88").assertDoesNotExist()
        compose.onNodeWithText("참고 점수 · 가속 흐름").assertDoesNotExist()
        compose.onNodeWithText("분석 가능한 기록이\n아직 부족해요").assertIsDisplayed()
        compose.onNodeWithText("기록으로 돌아가기").performScrollTo().assertIsDisplayed()
        capture("driving-insufficient")
    }

    @Test fun dataStatusShowsObservedCoverageAndNoInventedContext() {
        compose.setContent {
            PleosWindowTheme(Modifier.requiredSize(1216.dp, 760.dp)) {
                Box(Modifier.testTag("report-viewport")) { DrivingDataStatusScreen(record(), {}, {}) }
            }
        }
        compose.onNodeWithText("살펴볼 기록이 모였어요").assertIsDisplayed()
        compose.onNodeWithText("99%").assertIsDisplayed()
        compose.onNodeWithText("리포트 보기").performScrollTo().assertIsDisplayed()
        capture("driving-data-status")
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "screenshots").apply { mkdirs() }
        val file = File(directory, "$name.png")
        compose.onNodeWithTag("report-viewport").captureToImage().asAndroidBitmap().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun record() = JourneyRecord(
        "example", Instant.parse("2026-11-06T05:18:00Z"), Instant.parse("2026-11-06T05:28:00Z"),
        "Asia/Seoul", 5_000, AssessmentStatus.Complete, 88, 0,
        driving = DrivingAssessment(
            elapsedMillis = 600_000, observedMillis = 599_000, movingMillis = 599_000, sampleCount = 3_000,
            rejectedSamples = 0, harshAccelerations = 2, harshBrakings = 1, harshStarts = 0, longestGapMillis = 200,
        ),
    )
}
