package io.issac.pleosandroid.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
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
                uiState = MainUiState.Ready(
                    phase.value,
                    VehicleState(
                        motion = VehicleMotionState(
                            if (moving) VehicleMotion.Moving else VehicleMotion.Stopped,
                            if (moving) 13.9f else 0f,
                            MotionAvailability.Available,
                        ),
                        uxPolicy = if (moving) CarUxPolicy(connected = true, known = true) else unrestricted,
                        uxAvailability = UxAvailability.Available,
                        gear = if (phase.value == MainPhase.BeforeDriving || phase.value == MainPhase.Finished) {
                            VehicleGear.Park
                        } else {
                            VehicleGear.Drive
                        },
                    ),
                ),
                onStartJourney = {},
                onEndJourney = {},
                onReturnHome = {},
                onOpenBadges = {},
                onRequestSpeedPermission = {},
                onRetryVehicle = {},
            )
        }
        val labels = listOf("주행 전", "정차 중에도 조작은 잠시 쉬어요", "지금은 운전에 집중해요", "주행 종료")
        MainPhase.entries.forEachIndexed { index, state ->
            compose.runOnIdle { phase.value = state }
            compose.onNodeWithText(labels[index]).assertExists()
            if (state == MainPhase.DrivingMoving || state == MainPhase.DrivingStopped) {
                compose.onNodeWithText("내비게이션 안내는 시스템 화면에서 계속됩니다.").assertExists()
                compose.onNodeWithText("여정 종료").assertDoesNotExist()
                compose.onNodeWithText("컬렉션 보기").assertDoesNotExist()
            }
        }
    }

    @Test
    fun uxReadFailureKeepsActionsRestrictedAndRecoveryRestoresThem() {
        val vehicle = mutableStateOf(
            VehicleState(
                motion = VehicleMotionState(VehicleMotion.Stopped, 0f, MotionAvailability.Available),
                uxPolicy = CarUxPolicy(message = "차량 서비스 연결을 다시 시도합니다."),
                uxAvailability = UxAvailability.Error,
                gear = VehicleGear.Park,
            ),
        )
        compose.setContent {
            MainScreen(
                uiState = MainUiState.Ready(MainPhase.BeforeDriving, vehicle.value),
                onStartJourney = {},
                onEndJourney = {},
                onReturnHome = {},
                onOpenBadges = {},
                onRequestSpeedPermission = {},
                onRetryVehicle = {},
            )
        }
        compose.onNodeWithText("조작 가능 상태를 확인하고 있어요").assertExists()
        compose.onNodeWithText("여정 시작").assertDoesNotExist()
        compose.onNodeWithText("컬렉션 보기").assertDoesNotExist()
        compose.onNodeWithText("차량 속도 접근 허용").assertDoesNotExist()

        compose.runOnIdle {
            vehicle.value = vehicle.value.copy(
                uxPolicy = unrestricted,
                uxAvailability = UxAvailability.Available,
            )
        }

        compose.onNodeWithText("여정 시작").assertIsEnabled()
        compose.onNodeWithText("컬렉션 보기").assertIsEnabled()
        compose.onNodeWithText("조작 가능 상태를 확인하고 있어요").assertDoesNotExist()
    }

    private val unrestricted = CarUxPolicy(
        connected = true,
        known = true,
        requiresDistractionOptimization = false,
        activeRestrictions = 0,
    )
}
