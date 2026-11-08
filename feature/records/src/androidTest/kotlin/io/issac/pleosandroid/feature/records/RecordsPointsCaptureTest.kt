package io.issac.pleosandroid.feature.records

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import io.issac.pleosandroid.core.domain.points.PointEarningPolicy
import io.issac.pleosandroid.core.domain.points.PointWallet
import io.issac.pleosandroid.feature.records.history.RecordsScreen
import io.issac.pleosandroid.feature.records.history.RecordsState
import io.issac.pleosandroid.feature.records.points.DrivingPointsWeekScreen
import io.issac.pleosandroid.feature.records.points.RecordPoints
import io.issac.pleosandroid.feature.records.report.DrivingReportScreen
import java.io.File
import java.time.Instant
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Renders deterministic fixtures without writing journey records or point transactions. */
class RecordsPointsCaptureTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun captureRecordsAndPointsStates() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recordsPointsCapture") == "true")
        val earned = journey("earned", "2026-11-05T05:00:00Z", 12_000, 88, 1_800_000)
        val recordOnly = journey("record-only", "2026-11-05T06:00:00Z", 9_000, null, 480_000)
        val earlier = journey("earlier", "2026-11-03T05:00:00Z", 488_000, 80, 19_800_000)
        val credits = listOf(
            checkNotNull(PointEarningPolicy.settle(earned, earlier.distanceMeters)),
            checkNotNull(PointEarningPolicy.settle(earlier, 0)),
        )
        val points = RecordPoints.Ready(PointWallet(balance = credits.sumOf { it.delta }, transactions = credits))
        val records = RecordsState(
            loading = false,
            journeys = listOf(recordOnly, earned, earlier),
            visitedPlaceCount = 2,
            points = points,
        )
        val width = mutableStateOf(1682.dp)
        val current = mutableStateOf(Capture.Records)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.filesDir, "screenshots/records-points")
        check(output.isDirectory || output.mkdirs())

        compose.setContent {
            PleosWindowTheme(Modifier.requiredSize(width.value, 1210.dp)) {
                Column(Modifier.fillMaxSize().pleosShellBackground().testTag(CAPTURE_TAG)) {
                    PleosHeader(PleosTab.Records, {}, {}, restricted = false)
                    Box(Modifier.weight(1f)) {
                        key(current.value) {
                            when (val fixture = current.value) {
                                Capture.Records -> RecordsScreen(records, {}, {})

                                Capture.Earned, Capture.RecordOnly -> DrivingReportScreen(
                                    if (fixture == Capture.Earned) earned else recordOnly,
                                    demo = false,
                                    onBack = {},
                                    onDataStatus = {},
                                    onCollection = {},
                                    points = points,
                                )

                                Capture.Week, Capture.WeekLoading, Capture.WeekError -> DrivingPointsWeekScreen(
                                    state = records.copy(
                                        points = when (fixture) {
                                            Capture.WeekLoading -> RecordPoints.Loading
                                            Capture.WeekError -> RecordPoints.Unavailable
                                            else -> points
                                        },
                                    ),
                                    onBack = {},
                                    onRetry = {},
                                    now = Instant.parse("2026-11-06T12:00:00Z"),
                                )
                            }
                        }
                    }
                    PleosBottomTabs(PleosTab.Records, {}, restricted = false)
                }
            }
        }

        for (captureWidth in listOf(1682.dp, 841.dp)) {
            for (fixture in Capture.entries) {
                compose.runOnIdle {
                    width.value = captureWidth
                    current.value = fixture
                }
                compose.onNodeWithText(fixture.heading).assertIsDisplayed()
                val image = compose.onNodeWithTag(CAPTURE_TAG).captureToImage().asAndroidBitmap()
                File(output, "${fixture.filename}-${captureWidth.value.toInt()}.png").outputStream().use { stream ->
                    check(image.compress(Bitmap.CompressFormat.PNG, 100, stream))
                }
                for (label in fixture.content) {
                    compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
                }
            }
        }
    }

    private fun journey(id: String, end: String, distance: Long, score: Int?, movingMillis: Long): JourneyRecord {
        val endedAt = Instant.parse(end)
        val elapsedMillis = movingMillis + 300_000
        return JourneyRecord(
            id = id,
            startedAt = endedAt.minusMillis(elapsedMillis),
            endedAt = endedAt,
            zoneId = "Asia/Seoul",
            distanceMeters = distance,
            assessment = if (score == null) AssessmentStatus.Insufficient else AssessmentStatus.Complete,
            score = score,
            driving = DrivingAssessment(
                elapsedMillis = elapsedMillis,
                observedMillis = elapsedMillis,
                movingMillis = movingMillis,
                sampleCount = 1_200,
                rejectedSamples = 0,
                harshAccelerations = 1,
                harshBrakings = 0,
                harshStarts = 0,
                longestGapMillis = 200,
            ),
        )
    }

    private enum class Capture(val filename: String, val heading: String, vararg val content: String) {
        Records("e36-records", "주행 기록", "+316 P", "기록만 저장"),
        Earned("e37-earned", "이번 여정의 운전습관", "기본 포인트", "316 P"),
        RecordOnly("e37-record-only", "운전습관 기록 상태", "기록만 저장"),
        Week("e38-week", "이번 주 포인트", "509 km", "500 km"),
        WeekLoading("e38-loading", "이번 주 포인트", "509 km", "포인트를 확인하고 있어요."),
        WeekError("e38-error", "이번 주 포인트", "509 km", "다시 시도"),
    }

    private companion object {
        const val CAPTURE_TAG = "records-points-capture"
    }
}
