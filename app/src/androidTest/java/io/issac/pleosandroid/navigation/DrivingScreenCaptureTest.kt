package io.issac.pleosandroid.navigation

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.issac.pleosandroid.core.designsystem.component.PleosBottomTabs
import io.issac.pleosandroid.core.designsystem.component.PleosHeader
import io.issac.pleosandroid.core.designsystem.component.PleosTab
import io.issac.pleosandroid.core.designsystem.component.pleosShellBackground
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.UxAvailability
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.model.VehicleMotionState
import io.issac.pleosandroid.core.domain.model.VehicleState
import io.issac.pleosandroid.core.domain.policy.CarUxPolicy
import io.issac.pleosandroid.feature.main.JourneySummary
import io.issac.pleosandroid.feature.main.MainPhase
import io.issac.pleosandroid.feature.main.MainScreen
import io.issac.pleosandroid.feature.main.MainUiState
import java.io.File
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
        val cases = listOf(
            Capture(MainPhase.BeforeDriving, "주행 전", "01-before-driving"),
            Capture(MainPhase.DrivingStopped, "정차 중에도 조작은 잠시 쉬어요", "02-driving-stopped"),
            Capture(MainPhase.DrivingMoving, "지금은 운전에 집중해요", "03-driving-moving"),
            Capture(MainPhase.Finished, "주행 종료", "04-finished"),
            Capture(MainPhase.DrivingStopped, "도착했어요. 주차 후 이어가요", "05-arrived", arrived = true),
            Capture(MainPhase.BeforeDriving, "조작 가능 상태를 확인하고 있어요", "06-ux-unconfirmed", known = false),
        )
        val current = mutableStateOf(cases.first())
        val width = mutableStateOf(1216.dp)
        compose.setContent {
            PleosTheme {
                val state = fixture(current.value)
                Surface(
                    modifier = Modifier.size(width.value, 760.dp).testTag("driving-state-capture"),
                    color = PleosTheme.colors.background,
                ) {
                    Column(Modifier.pleosShellBackground()) {
                        PleosHeader(PleosTab.Travel, {}, {}, restricted = !state.canUseTabs)
                        Box(Modifier.weight(1f)) {
                            MainScreen(
                                uiState = state,
                                onStartJourney = {},
                                onEndJourney = {},
                                onReturnHome = {},
                                onOpenBadges = {},
                                onRequestSpeedPermission = {},
                                onRetryVehicle = {},
                                onOpenMap = {},
                            )
                        }
                        PleosBottomTabs(PleosTab.Travel, {}, restricted = !state.canUseTabs)
                    }
                }
            }
        }
        val outputDirectory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "screenshots",
        )
        check(outputDirectory.isDirectory || outputDirectory.mkdirs())
        for ((layout, captureWidth) in listOf("full" to 1216.dp, "split" to 520.dp)) {
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
            summary = if (phase == MainPhase.Finished) JourneySummary(durationMillis = 23 * 60_000L) else null,
            arrivedBeforeParking = capture.arrived,
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
                    requiresDistractionOptimization = moving,
                    activeRestrictions = if (moving) CarUxPolicy.FULLY_RESTRICTED else 0,
                ),
                uxAvailability = UxAvailability.Available,
                gear = if (phase == MainPhase.BeforeDriving || phase == MainPhase.Finished) {
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
    )
}
