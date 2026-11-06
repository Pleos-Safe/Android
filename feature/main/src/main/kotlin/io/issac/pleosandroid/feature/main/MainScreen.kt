package io.issac.pleosandroid.feature.main

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosScreen
import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.VehicleGear

@Composable
fun MainScreen(
    uiState: MainUiState,
    onStartJourney: () -> Unit,
    onEndJourney: () -> Unit,
    onReturnHome: () -> Unit,
    onOpenBadges: () -> Unit,
    onRequestSpeedPermission: () -> Unit,
    onRetryVehicle: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenMap: (() -> Unit)? = null,
    drivingRecommendation: @Composable (() -> Unit)? = null,
) {
    val restriction = (uiState as? MainUiState.Ready)?.restriction
    if (restriction != null) {
        val recommendation = when (restriction) {
            JourneyRestriction.Moving, JourneyRestriction.Stopped -> drivingRecommendation
            JourneyRestriction.Arrived, JourneyRestriction.Unconfirmed -> null
        }
        JourneyRestrictionScreen(restriction, modifier, recommendation)
        return
    }
    if (uiState is MainUiState.Ready) {
        if (uiState.canReturnHome) {
            ParkedJourneySummary(
                summary = uiState.summary,
                onReturnHome = onReturnHome,
                onStartJourney = onStartJourney,
                onOpenBadges = onOpenBadges,
                modifier = modifier,
            )
            return
        }
        if (uiState.phase == MainPhase.BeforeDriving && uiState.canUseTabs) {
            ParkedTravelHome(
                onStartJourney = onStartJourney,
                onOpenBadges = onOpenBadges,
                modifier = modifier,
                onOpenMap = onOpenMap,
            )
            return
        }
    }
    PleosScreen(title = "메인", modifier = modifier) {
        when (uiState) {
            MainUiState.Loading -> Text("차량 상태를 확인하고 있습니다.")

            is MainUiState.Ready -> {
                Text(
                    when (uiState.phase) {
                        MainPhase.BeforeDriving -> "주행 전"
                        MainPhase.DrivingStopped -> "주행 중 · 멈춤"
                        MainPhase.DrivingMoving -> "주행 중 · 달림"
                        MainPhase.Finished -> "주행 종료"
                        null -> "주행 상태 확인 중"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(uiState.vehicle.motion.message)
                uiState.vehicle.motion.speedMetersPerSecond?.let { speed ->
                    val locale = LocalConfiguration.current.locales[0]
                    Text(String.format(locale, "현재 속도: %.1f km/h", speed * 3.6f))
                }
                if (!uiState.canUseTabs) {
                    Text(
                        if (uiState.vehicle.gear == VehicleGear.Unknown) {
                            "주차 상태를 확인하고 있습니다."
                        } else {
                            "주차와 정지가 확인되면 탭을 이용할 수 있습니다."
                        },
                    )
                    if (uiState.vehicle.allowsVehicleRecovery) {
                        if (uiState.vehicle.motion.availability == MotionAvailability.PermissionRequired) {
                            PleosButton(text = "차량 속도 접근 허용", onClick = onRequestSpeedPermission)
                        }
                        if (uiState.vehicle.motion.availability != MotionAvailability.Available) {
                            PleosButton(text = "차량 상태 다시 확인", onClick = onRetryVehicle)
                        }
                    }
                    return@PleosScreen
                }
                if (uiState.phase == MainPhase.DrivingStopped ||
                    uiState.phase == MainPhase.DrivingMoving ||
                    uiState.phase == null
                ) {
                    PleosButton(
                        text = "여정 종료",
                        onClick = onEndJourney,
                        enabled = uiState.canEndJourney,
                    )
                }
                PleosButton(text = "컬렉션 보기", onClick = onOpenBadges, enabled = uiState.canOpenBadges)
                Text("출발하면 주행을 시작하고, 멈추면 주행 중 멈춤으로 표시하며, 정지 후 P 기어에서 종료합니다.")
            }
        }
    }
}

private val MainUiState.Ready.restriction: JourneyRestriction?
    get() = when {
        !vehicle.uxPolicy.connected || !vehicle.uxPolicy.known -> JourneyRestriction.Unconfirmed

        phase == MainPhase.DrivingMoving -> JourneyRestriction.Moving

        phase == MainPhase.DrivingStopped && vehicle.gear != VehicleGear.Park &&
            vehicle.gear != VehicleGear.Unknown -> if (arrivedBeforeParking) {
            JourneyRestriction.Arrived
        } else {
            JourneyRestriction.Stopped
        }

        vehicle.uxPolicy.restrictionsRequired -> JourneyRestriction.Unconfirmed

        else -> null
    }
