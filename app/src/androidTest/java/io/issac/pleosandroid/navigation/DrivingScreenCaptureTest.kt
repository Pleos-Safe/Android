package io.issac.pleosandroid.navigation

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.issac.pleosandroid.core.designsystem.component.PleosBottomTabs
import io.issac.pleosandroid.core.designsystem.component.PleosHeader
import io.issac.pleosandroid.core.designsystem.component.PleosTab
import io.issac.pleosandroid.core.designsystem.component.pleosShellBackground
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.designsystem.theme.PleosWindowTheme
import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimResult
import io.issac.pleosandroid.core.domain.collection.model.VisitPackReceipt
import io.issac.pleosandroid.core.domain.driving.repository.RecordingPhase
import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.UxAvailability
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.model.VehicleMotionState
import io.issac.pleosandroid.core.domain.model.VehicleState
import io.issac.pleosandroid.core.domain.policy.CarUxPolicy
import io.issac.pleosandroid.feature.main.JourneyProgressDisplay
import io.issac.pleosandroid.feature.main.JourneySummary
import io.issac.pleosandroid.feature.main.MainPhase
import io.issac.pleosandroid.feature.main.MainScreen
import io.issac.pleosandroid.feature.main.MainUiState
import io.issac.pleosandroid.feature.main.VisitPackRewardCard
import java.io.File
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Renders UI fixtures only; this test never reads or changes vehicle properties. */
@RunWith(AndroidJUnit4::class)
class DrivingScreenCaptureTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun captureFourDrivingStates() {
        capture(
            cases = listOf(
                Capture(MainPhase.BeforeDriving, "주행 전", "01-before-driving"),
                Capture(MainPhase.DrivingStopped, "정차 중에도 조작은 잠시 쉬어요", "02-driving-stopped"),
                Capture(MainPhase.DrivingMoving, "지금은 운전에 집중해요", "03-driving-moving"),
                Capture(MainPhase.Finished, "주행 종료", "04-finished"),
                Capture(MainPhase.DrivingStopped, "도착했어요. 주차 후 이어가요", "05-arrived", arrived = true),
                Capture(MainPhase.BeforeDriving, "조작 가능 상태를 확인하고 있어요", "06-ux-unconfirmed", known = false),
                Capture(MainPhase.BeforeDriving, "출발하면 여정이 시작돼요", "07-drive-before-motion", driveBeforeMotion = true),
            ),
            layouts = listOf("full" to 1216.dp, "split" to 520.dp),
        )
    }

    @Test
    fun captureJourneyRewardStates() {
        val progress = JourneyProgressDisplay(18_400, 23 * 60_000L)
        val earned = JourneySummary(
            durationMillis = progress.movingMillis,
            reportId = "preview-journey",
            recordingPhase = RecordingPhase.Saved,
            distanceMeters = progress.distanceMeters,
            safetyScore = 93,
            earnedPoints = 513,
            recognizedDistanceMeters = progress.distanceMeters,
        )
        capture(
            cases = listOf(
                Capture(MainPhase.BeforeDriving, "주행 전", "08-home-wallet", pointBalance = 2_450),
                Capture(MainPhase.DrivingMoving, "주행 기록 중", "09-live-recording", progress = progress),
                Capture(
                    MainPhase.DrivingStopped,
                    "잠시 주차했어요",
                    "10-parked-pause",
                    progress = progress,
                    paused = true,
                ),
                Capture(
                    MainPhase.DrivingStopped,
                    "이전 주행 이어가기",
                    "11-recovery",
                    progress = progress,
                    paused = true,
                    recovering = true,
                ),
                Capture(MainPhase.Finished, "+513 P", "12-summary-earned", summary = earned),
                Capture(
                    MainPhase.Finished,
                    "이번 주행은 기록만 남겼어요",
                    "13-summary-no-points",
                    summary = earned.copy(
                        durationMillis = 8 * 60_000L,
                        distanceMeters = 4_200,
                        safetyScore = null,
                        earnedPoints = 0,
                        recognizedDistanceMeters = 0,
                        settlementReason = "10분 이상, 5km 이상 달리면 포인트를 받아요.",
                    ),
                ),
                Capture(
                    MainPhase.Finished,
                    "이번 주 포인트 인정 거리를 모두 사용했어요",
                    "14-summary-weekly-cap",
                    summary = earned.copy(
                        earnedPoints = 0,
                        recognizedDistanceMeters = 0,
                        settlementReason = "이번 주 포인트 인정 거리를 모두 사용했어요",
                    ),
                ),
                Capture(
                    MainPhase.DrivingStopped,
                    "방문 보상을 받을 수 있어요",
                    "15-visit-reward-available",
                    progress = progress,
                    paused = true,
                    visit = VisitReward.Available,
                ),
                Capture(
                    MainPhase.DrivingStopped,
                    "방문 카드팩 1개를 받았어요",
                    "16-visit-reward-claimed",
                    progress = progress,
                    paused = true,
                    visit = VisitReward.Claimed,
                ),
            ),
            layouts = listOf("full-1682" to 1682.dp, "split-841" to 841.dp),
            height = 1210.dp,
        )
    }

    private fun capture(cases: List<Capture>, layouts: List<Pair<String, Dp>>, height: Dp = 760.dp) {
        val current = mutableStateOf(cases.first())
        val width = mutableStateOf(layouts.first().second)
        compose.setContent {
            BoxWithConstraints {
                val deviceDensity = LocalDensity.current
                val viewportDensity = Density(
                    density = deviceDensity.density * minOf(1f, maxWidth / width.value, maxHeight / height),
                    fontScale = deviceDensity.fontScale,
                )
                CompositionLocalProvider(LocalDensity provides viewportDensity) {
                    PleosWindowTheme(Modifier.size(width.value, height)) {
                        key(current.value.filename) {
                            val state = fixture(current.value)
                            Surface(
                                modifier = Modifier.size(width.value, height).testTag("driving-state-capture"),
                                color = PleosTheme.colors.background,
                            ) {
                                Column(Modifier.pleosShellBackground()) {
                                    PleosHeader(PleosTab.Travel, {}, {}, restricted = !state.canUseTabs)
                                    Box(Modifier.weight(1f)) {
                                        MainScreen(
                                            uiState = state,
                                            onReturnHome = {},
                                            onOpenBadges = {},
                                            onRequestSpeedPermission = {},
                                            onRetryVehicle = {},
                                            onOpenMap = {},
                                            visitReward = {
                                                current.value.visit?.let {
                                                    VisitPackRewardCard(
                                                        "해운대 해변",
                                                        it.result,
                                                        onClaim = if (it == VisitReward.Available) ({}) else null,
                                                    )
                                                }
                                            },
                                        )
                                    }
                                    PleosBottomTabs(PleosTab.Travel, {}, restricted = !state.canUseTabs)
                                }
                            }
                        }
                    }
                }
            }
        }
        val outputDirectory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "screenshots",
        )
        check(outputDirectory.isDirectory || outputDirectory.mkdirs())
        for ((layout, captureWidth) in layouts) {
            for (capture in cases) {
                compose.runOnIdle {
                    current.value = capture
                    width.value = captureWidth
                }
                compose.onNodeWithText(capture.label).assertExists()
                compose.waitForIdle()
                val image = compose.onNodeWithTag("driving-state-capture").captureToImage().asAndroidBitmap()
                File(outputDirectory, "${capture.filename}-$layout.png").outputStream().use { stream ->
                    check(image.compress(Bitmap.CompressFormat.PNG, 100, stream))
                }
            }
        }
    }

    private fun fixture(capture: Capture): MainUiState.Ready {
        val phase = capture.phase
        val moving = phase == MainPhase.DrivingMoving
        return MainUiState.Ready(
            phase = phase,
            summary = capture.summary ?: if (phase == MainPhase.Finished) {
                JourneySummary(durationMillis = 23 * 60_000L)
            } else {
                null
            },
            arrivedBeforeParking = capture.arrived,
            progress = capture.progress,
            journeyPaused = capture.paused,
            recoveryRequired = capture.recovering,
            pointBalance = capture.pointBalance,
            vehicle = VehicleState(
                motion = VehicleMotionState(
                    motion = if (moving) VehicleMotion.Moving else VehicleMotion.Stopped,
                    speedMetersPerSecond = if (moving) 13.9f else 0f,
                    availability = MotionAvailability.Available,
                    message = if (moving) "차량이 이동 중입니다." else "차량이 멈춰 있습니다.",
                ),
                uxPolicy = CarUxPolicy(
                    connected = true,
                    known = capture.known,
                    requiresDistractionOptimization = moving || capture.driveBeforeMotion,
                    activeRestrictions = if (moving) CarUxPolicy.FULLY_RESTRICTED else 0,
                ),
                uxAvailability = UxAvailability.Available,
                gear = if (!capture.driveBeforeMotion &&
                    (phase == MainPhase.BeforeDriving || phase == MainPhase.Finished || capture.paused)
                ) {
                    VehicleGear.Park
                } else {
                    VehicleGear.Drive
                },
            ),
        )
    }

    private data class Capture(
        val phase: MainPhase,
        val label: String,
        val filename: String,
        val arrived: Boolean = false,
        val known: Boolean = true,
        val driveBeforeMotion: Boolean = false,
        val progress: JourneyProgressDisplay? = null,
        val summary: JourneySummary? = null,
        val paused: Boolean = false,
        val recovering: Boolean = false,
        val pointBalance: Long? = null,
        val visit: VisitReward? = null,
    )

    private enum class VisitReward(val result: VisitPackClaimResult?) {
        Available(null),
        Claimed(
            VisitPackClaimResult.Applied(
                VisitPackReceipt(
                    id = "preview-visit",
                    journeyId = "preview-journey",
                    placeId = "haeundae-beach",
                    placeTitle = "해운대 해변",
                    category = "자연·산책",
                    claimedAt = Instant.EPOCH,
                ),
            ),
        ),
    }
}
