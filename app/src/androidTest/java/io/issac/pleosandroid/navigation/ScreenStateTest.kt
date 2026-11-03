package io.issac.pleosandroid.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.issac.pleosandroid.core.vehicle.policy.CarUxPolicy
import io.issac.pleosandroid.core.vehicle.model.MotionAvailability
import io.issac.pleosandroid.core.vehicle.model.UxAvailability
import io.issac.pleosandroid.core.vehicle.model.VehicleGear
import io.issac.pleosandroid.core.vehicle.model.VehicleMotion
import io.issac.pleosandroid.core.vehicle.model.VehicleMotionState
import io.issac.pleosandroid.core.vehicle.model.VehicleState
import io.issac.pleosandroid.feature.main.MainPhase
import io.issac.pleosandroid.feature.main.MainScreen
import io.issac.pleosandroid.feature.main.MainUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenStateTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun oneMainScreenRendersAllFourPhases() {
        val phase = mutableStateOf(MainPhase.BeforeDriving)
        compose.setContent {
            val moving = phase.value == MainPhase.DrivingMoving
            MainScreen(
                uiState = MainUiState.Ready(phase.value, VehicleState(
                    motion = VehicleMotionState(
                        if (moving) VehicleMotion.Moving else VehicleMotion.Stopped,
                        if (moving) 13.9f else 0f,
                        MotionAvailability.Available,
                    ),
                    uxPolicy = if (moving) CarUxPolicy(connected = true, known = true) else unrestricted,
                    uxAvailability = UxAvailability.Available,
                    gear = if (phase.value == MainPhase.BeforeDriving || phase.value == MainPhase.Finished) {
                        VehicleGear.Park
                    } else VehicleGear.Drive,
                )),
                onStartJourney = {}, onEndJourney = {}, onOpenBadges = {},
                onRequestSpeedPermission = {}, onRetryVehicle = {},
            )
        }
        val labels = listOf("주행 전", "주행 중 · 멈춤", "주행 중 · 달림", "주행 종료")
        MainPhase.entries.forEachIndexed { index, state ->
            compose.runOnIdle { phase.value = state }
            compose.onNodeWithText(labels[index]).assertExists()
            if (state == MainPhase.DrivingMoving) {
                compose.onNodeWithText("주행 중에는 상세 조작이 제한됩니다.").assertExists()
                compose.onNodeWithText("여정 종료").assertDoesNotExist()
                compose.onNodeWithText("배지 보기").assertDoesNotExist()
            }
        }
    }

    @Test
    fun uxReadFailureKeepsActionsRestrictedAndRecoveryRestoresThem() {
        val vehicle = mutableStateOf(VehicleState(
            motion = VehicleMotionState(VehicleMotion.Stopped, 0f, MotionAvailability.Available),
            uxPolicy = CarUxPolicy(message = "차량 서비스 연결을 다시 시도합니다."),
            uxAvailability = UxAvailability.Error,
            gear = VehicleGear.Park,
        ))
        compose.setContent {
            MainScreen(
                uiState = MainUiState.Ready(MainPhase.BeforeDriving, vehicle.value),
                onStartJourney = {}, onEndJourney = {}, onOpenBadges = {},
                onRequestSpeedPermission = {}, onRetryVehicle = {},
            )
        }
        compose.onNodeWithText("차량 UX 제한 상태를 읽지 못해 조작을 제한합니다.").assertExists()
        compose.onNodeWithText("여정 시작").assertDoesNotExist()
        compose.onNodeWithText("배지 보기").assertDoesNotExist()
        compose.onNodeWithText("차량 속도 접근 허용").assertDoesNotExist()

        compose.runOnIdle {
            vehicle.value = vehicle.value.copy(
                uxPolicy = unrestricted,
                uxAvailability = UxAvailability.Available,
            )
        }

        compose.onNodeWithText("여정 시작").assertIsEnabled()
        compose.onNodeWithText("배지 보기").assertIsEnabled()
        compose.onNodeWithText("차량 UX 제한 상태를 읽지 못해 조작을 제한합니다.").assertDoesNotExist()
    }

    private val unrestricted = CarUxPolicy(
        connected = true, known = true, requiresDistractionOptimization = false, activeRestrictions = 0,
    )
}
