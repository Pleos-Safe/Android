package io.issac.pleosandroid.feature.home

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.UxAvailability
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.model.VehicleMotionState
import io.issac.pleosandroid.core.domain.model.VehicleState
import io.issac.pleosandroid.core.domain.place.DrivingSafetyMessage
import io.issac.pleosandroid.core.domain.place.PlaceNavigationOperation
import io.issac.pleosandroid.core.domain.policy.CarUxPolicy
import io.issac.pleosandroid.feature.home.driving.DrivingOverviewScreen
import io.issac.pleosandroid.feature.home.driving.DrivingRecommendation
import io.issac.pleosandroid.feature.home.driving.DrivingRegionMap
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DrivingRecommendationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsOnlyTheActionThatMatchesTheConfirmedRoute() {
        var hasDestination by mutableStateOf<Boolean?>(false)
        var requests = 0
        compose.setContent {
            PleosTheme {
                DrivingRecommendation(
                    placeName = "해운대 해수욕장",
                    hasDestination = hasDestination,
                    operation = PlaceNavigationOperation.Idle,
                    onRequest = { requests++ },
                    onRecheck = {},
                )
            }
        }

        compose.onAllNodesWithText("목적지로 설정").assertCountEquals(1)
        compose.onNodeWithText("경유지로 추가").assertDoesNotExist()
        compose.onNodeWithText("목적지로 설정").performClick()

        compose.runOnIdle {
            assertEquals(1, requests)
            hasDestination = true
        }
        compose.onAllNodesWithText("경유지로 추가").assertCountEquals(1)
        compose.onNodeWithText("목적지로 설정").assertDoesNotExist()
        compose.onNodeWithText("경유지로 추가").performClick()

        compose.runOnIdle {
            assertEquals(2, requests)
            hasDestination = null
        }
        assertNoRequestActions()
    }

    @Test
    fun pendingRequestHidesActionsAndUnconfirmedResultOnlyOffersRecheck() {
        var operation by mutableStateOf(PlaceNavigationOperation.Requesting)
        var requests = 0
        var rechecks = 0
        compose.setContent {
            PleosTheme {
                DrivingRecommendation(
                    placeName = "해운대 해수욕장",
                    hasDestination = true,
                    operation = operation,
                    onRequest = { requests++ },
                    onRecheck = {
                        rechecks++
                        operation = PlaceNavigationOperation.Checking
                    },
                )
            }
        }

        compose.onNodeWithText("내비에 전달 중").assertExists()
        assertNoRequestActions()
        compose.onNodeWithText("현재 경로 확인").assertDoesNotExist()

        compose.runOnIdle { operation = PlaceNavigationOperation.Rejected }
        compose.onNodeWithText("내비에 반영되지 않았어요").assertExists()
        compose.onNodeWithText("경유지로 추가").assertIsEnabled()
        compose.onNodeWithText("현재 경로 확인").assertDoesNotExist()

        compose.runOnIdle { operation = PlaceNavigationOperation.Unconfirmed }
        compose.onNodeWithText("경로 변경을 확인하지 못했어요").assertExists()
        assertNoRequestActions()
        compose.onNodeWithText("현재 경로 확인").performClick()

        compose.onNodeWithText("현재 경로 확인 중").assertExists()
        compose.onNodeWithText("현재 경로 확인").assertDoesNotExist()
        assertNoRequestActions()
        compose.runOnIdle {
            assertEquals(0, requests)
            assertEquals(1, rechecks)
        }
    }

    @Test
    fun mainScreenShowsRecommendationOnlyInMovingAndStoppedDrivingViews() {
        val moving = VehicleState(
            motion = VehicleMotionState(VehicleMotion.Moving, availability = MotionAvailability.Available),
            uxPolicy = CarUxPolicy(connected = true, known = true),
            uxAvailability = UxAvailability.Available,
            gear = VehicleGear.Drive,
        )
        val stopped = moving.copy(motion = moving.motion.copy(motion = VehicleMotion.Stopped))
        val parked = stopped.copy(
            uxPolicy = moving.uxPolicy.copy(requiresDistractionOptimization = false),
            gear = VehicleGear.Park,
        )
        var uiState by mutableStateOf(HomeUiState.Ready(HomePhase.DrivingMoving, moving))
        compose.setContent {
            PleosTheme {
                HomeScreen(
                    uiState = uiState,
                    onReturnHome = {},
                    onOpenBadges = {},
                    onRequestSpeedPermission = {},
                    onRetryVehicle = {},
                    drivingRecommendation = { Text("주변 장소 추천") },
                )
            }
        }

        compose.onNodeWithText("주변 장소 추천").assertExists()

        compose.runOnIdle { uiState = HomeUiState.Ready(HomePhase.DrivingStopped, stopped) }
        compose.onNodeWithText("정차 중에도 조작은 잠시 쉬어요").assertExists()
        compose.onNodeWithText("주변 장소 추천").assertExists()

        compose.runOnIdle { uiState = uiState.copy(arrivedBeforeParking = true) }
        compose.onNodeWithText("도착했어요. 주차 후 이어가요").assertExists()
        compose.onNodeWithText("주변 장소 추천").assertDoesNotExist()

        compose.runOnIdle {
            uiState = HomeUiState.Ready(
                HomePhase.DrivingMoving,
                moving.copy(uxPolicy = CarUxPolicy(), uxAvailability = UxAvailability.Error),
            )
        }
        compose.onNodeWithText("조작 가능 상태를 확인하고 있어요").assertExists()
        compose.onNodeWithText("주변 장소 추천").assertDoesNotExist()

        compose.runOnIdle { uiState = HomeUiState.Ready(HomePhase.BeforeDriving, parked) }
        compose.onNodeWithText("주행 전").assertExists()
        compose.onNodeWithText("주변 장소 추천").assertDoesNotExist()
    }

    private fun assertNoRequestActions() {
        compose.onNodeWithText("목적지로 설정").assertDoesNotExist()
        compose.onNodeWithText("경유지로 추가").assertDoesNotExist()
    }
}
