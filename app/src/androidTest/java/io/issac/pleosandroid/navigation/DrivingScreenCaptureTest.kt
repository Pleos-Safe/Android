package io.issac.pleosandroid.navigation

import android.graphics.Bitmap
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
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.UxAvailability
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.model.VehicleMotionState
import io.issac.pleosandroid.core.domain.model.VehicleState
import io.issac.pleosandroid.core.domain.policy.CarUxPolicy
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
        val phase = mutableStateOf(MainPhase.BeforeDriving)
        compose.setContent {
            PleosTheme {
                Surface(
                    modifier = Modifier.size(960.dp, 540.dp).testTag("driving-state-capture"),
                    color = PleosTheme.colors.background,
                ) {
                    MainScreen(
                        uiState = fixture(phase.value),
                        onStartJourney = {}, onEndJourney = {}, onOpenBadges = {},
                        onRequestSpeedPermission = {}, onRetryVehicle = {},
                    )
                }
            }
        }
        val outputDirectory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "screenshots",
        )
        check(outputDirectory.isDirectory || outputDirectory.mkdirs())
        val cases = listOf(
            Capture(MainPhase.BeforeDriving, "주행 전", "01-before-driving.png"),
            Capture(MainPhase.DrivingStopped, "주행 중 · 멈춤", "02-driving-stopped.png"),
            Capture(MainPhase.DrivingMoving, "주행 중 · 달림", "03-driving-moving.png"),
            Capture(MainPhase.Finished, "주행 종료", "04-finished.png"),
        )
        cases.forEach { capture ->
            compose.runOnIdle { phase.value = capture.phase }
            compose.onNodeWithText(capture.label).assertExists()
            compose.waitForIdle()
            val image = compose.onNodeWithTag("driving-state-capture").captureToImage().asAndroidBitmap()
            File(outputDirectory, capture.filename).outputStream().use { stream ->
                check(image.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        }
    }

    private fun fixture(phase: MainPhase): MainUiState.Ready {
        val moving = phase == MainPhase.DrivingMoving
        return MainUiState.Ready(
            phase = phase,
            vehicle = VehicleState(
                motion = VehicleMotionState(
                    motion = if (moving) VehicleMotion.Moving else VehicleMotion.Stopped,
                    speedMetersPerSecond = if (moving) 13.9f else 0f,
                    availability = MotionAvailability.Available,
                    message = if (moving) "차량이 이동 중입니다." else "차량이 멈춰 있습니다.",
                ),
                uxPolicy = CarUxPolicy(
                    connected = true,
                    known = true,
                    requiresDistractionOptimization = moving,
                    activeRestrictions = if (moving) CarUxPolicy.FULLY_RESTRICTED else 0,
                ),
                uxAvailability = UxAvailability.Available,
                gear = if (phase == MainPhase.BeforeDriving || phase == MainPhase.Finished) {
                    VehicleGear.Park
                } else VehicleGear.Drive,
            ),
        )
    }

    private data class Capture(val phase: MainPhase, val label: String, val filename: String)
}
