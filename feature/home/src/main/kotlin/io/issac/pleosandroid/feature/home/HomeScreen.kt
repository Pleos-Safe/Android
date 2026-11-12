package io.issac.pleosandroid.feature.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosInfoPanel
import io.issac.pleosandroid.core.designsystem.component.PleosScreen
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.feature.home.driving.DrivingOverviewScreen
import io.issac.pleosandroid.feature.home.driving.DrivingRegionMap
import io.issac.pleosandroid.feature.home.driving.JourneyRestriction
import io.issac.pleosandroid.feature.home.driving.JourneyRestrictionScreen
import io.issac.pleosandroid.feature.home.driving.NoDrivingRecommendation
import io.issac.pleosandroid.feature.home.journey.ParkedJourneyPause
import io.issac.pleosandroid.feature.home.journey.ParkedJourneySummary
import io.issac.pleosandroid.feature.home.journey.ParkedTravelHome

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onReturnHome: () -> Unit,
    onOpenBadges: () -> Unit,
    onRequestSpeedPermission: () -> Unit,
    onRetryVehicle: () -> Unit,
    modifier: Modifier = Modifier,
    homePlace: HomePlace? = null,
    onOpenMap: (() -> Unit)? = null,
    onOpenRecords: () -> Unit = {},
    onOpenReport: (String) -> Unit = {},
    drivingRecommendation: @Composable (() -> Unit)? = null,
    drivingContent: @Composable ((Boolean, Modifier) -> Unit)? = null,
    onFinishJourney: () -> Unit = {},
    onContinueJourney: () -> Unit = {},
    visitReward: @Composable () -> Unit = {},
) {
    val restriction = (uiState as? HomeUiState.Ready)?.restriction
    if (restriction != null) {
        if (restriction == JourneyRestriction.Moving || restriction == JourneyRestriction.Stopped) {
            val isMoving = restriction == JourneyRestriction.Moving
            if (drivingContent != null) {
                drivingContent(isMoving, modifier)
            } else {
                DrivingOverviewScreen(
                    isMoving = isMoving,
                    progress = (uiState as HomeUiState.Ready).progress,
                    modifier = modifier,
                    map = { DrivingRegionMap(null, emptyMap(), isMoving, it) },
                    recommendation = { drivingRecommendation?.invoke() ?: NoDrivingRecommendation() },
                )
            }
        } else {
            JourneyRestrictionScreen(restriction, modifier)
        }
        return
    }
    if (uiState is HomeUiState.Ready) {
        if (uiState.canManagePausedJourney) {
            ParkedJourneyPause(
                progress = uiState.progress,
                recovering = uiState.recoveryRequired,
                pausedAtNanos = uiState.pausedAtNanos,
                busy = uiState.journeyActionPending,
                error = uiState.journeyActionError,
                onContinueJourney = onContinueJourney,
                onFinishJourney = onFinishJourney,
                visitReward = visitReward,
                modifier = modifier,
            )
            return
        }
        if (uiState.canReturnHome) {
            ParkedJourneySummary(
                summary = uiState.summary,
                onOpenRecords = onOpenRecords,
                onOpenReport = onOpenReport,
                onReturnHome = onReturnHome,
                onOpenBadges = onOpenBadges,
                modifier = modifier,
            )
            return
        }
        if (uiState.phase == HomePhase.BeforeDriving && uiState.canUseTabs) {
            ParkedTravelHome(
                place = homePlace,
                pointBalance = uiState.pointBalance,
                demoWallet = uiState.demoWallet,
                onOpenBadges = onOpenBadges,
                modifier = modifier,
                onOpenMap = onOpenMap,
                onOpenRecords = onOpenRecords,
            )
            return
        }
    }
    VehicleStatusScreen(uiState, onOpenBadges, onRequestSpeedPermission, onRetryVehicle, modifier)
}

@Composable
private fun VehicleStatusScreen(
    uiState: HomeUiState,
    onOpenBadges: () -> Unit,
    onRequestSpeedPermission: () -> Unit,
    onRetryVehicle: () -> Unit,
    modifier: Modifier,
) {
    PleosScreen(title = "차량 상태", modifier = modifier) {
        when (uiState) {
            HomeUiState.Loading -> PleosInfoPanel(Modifier.fillMaxWidth()) {
                Text("차량에 연결하고 있어요", style = PleosTextStyles.section, color = PleosTheme.colors.textPrimary)
            }

            is HomeUiState.Ready -> {
                Text(
                    when (uiState.phase) {
                        HomePhase.BeforeDriving -> "주행 전"
                        HomePhase.DrivingStopped -> "주행 중 · 멈춤"
                        HomePhase.DrivingMoving -> "주행 중 · 달림"
                        HomePhase.Finished -> "주행 종료"
                        null -> "주행 상태 확인 중"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(uiState.vehicle.motion.message)
                uiState.vehicle.motion.speedMetersPerSecond?.let { speed ->
                    val locale = LocalConfiguration.current.locales[0]
                    Text(String.format(locale, "현재 속도 %.1f km/h", speed * 3.6f))
                }
                if (!uiState.canUseTabs) {
                    Text(
                        if (uiState.vehicle.gear == VehicleGear.Unknown) {
                            "주차 상태를 확인하고 있어요."
                        } else {
                            "P단에 주차하면 다른 탭을 이용할 수 있어요."
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
                PleosButton(text = "컬렉션 보기", onClick = onOpenBadges, enabled = uiState.canOpenBadges)
                Text("출발하면 여정이 시작돼요. P단에 주차한 뒤 여정을 마칠 수 있어요.")
            }
        }
    }
}

private val HomeUiState.Ready.restriction: JourneyRestriction?
    get() = when {
        !vehicle.uxPolicy.connected || !vehicle.uxPolicy.known -> JourneyRestriction.Unconfirmed

        phase == HomePhase.DrivingMoving -> JourneyRestriction.Moving

        phase == HomePhase.DrivingStopped && vehicle.gear != VehicleGear.Park &&
            vehicle.gear != VehicleGear.Unknown -> if (arrivedBeforeParking) {
            JourneyRestriction.Arrived
        } else {
            JourneyRestriction.Stopped
        }

        phase == HomePhase.BeforeDriving && vehicle.gear == VehicleGear.Drive &&
            vehicle.motion.availability == MotionAvailability.Available &&
            vehicle.motion.motion == VehicleMotion.Stopped -> JourneyRestriction.ReadyToDrive

        vehicle.uxPolicy.restrictionsRequired -> JourneyRestriction.Unconfirmed

        else -> null
    }
