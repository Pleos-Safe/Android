package io.issac.pleosandroid.feature.main

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosScreen
import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.UxAvailability

@Composable
fun MainScreen(
    uiState: MainUiState,
    onStartJourney: () -> Unit,
    onEndJourney: () -> Unit,
    onOpenBadges: () -> Unit,
    onRequestSpeedPermission: () -> Unit,
    onRetryVehicle: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                if (uiState.vehicle.uxPolicy.restrictionsRequired) {
                    Text(
                        when {
                            uiState.vehicle.uxPolicy.known -> "주행 중에는 상세 조작이 제한됩니다."
                            uiState.vehicle.uxAvailability == UxAvailability.Error ->
                                "차량 UX 제한 상태를 읽지 못해 조작을 제한합니다."
                            uiState.vehicle.uxAvailability == UxAvailability.Unsupported ->
                                "차량 UX 제한 정보를 지원하지 않는 환경입니다."
                            else -> "차량 UX 제한 상태를 확인하고 있습니다."
                        },
                    )
                    if (!uiState.vehicle.uxPolicy.known) Text(uiState.vehicle.uxPolicy.message)
                    return@PleosScreen
                }
                if (uiState.phase == MainPhase.BeforeDriving || uiState.phase == MainPhase.Finished) {
                    PleosButton(
                        text = if (uiState.phase == MainPhase.Finished) "새 여정 시작" else "여정 시작",
                        onClick = onStartJourney,
                        enabled = uiState.canStartJourney,
                    )
                }
                if (uiState.phase == MainPhase.DrivingStopped || uiState.phase == MainPhase.DrivingMoving ||
                    uiState.phase == null
                ) {
                    PleosButton(text = "여정 종료", onClick = onEndJourney, enabled = uiState.canEndJourney)
                }
                if (uiState.vehicle.motion.availability == MotionAvailability.PermissionRequired) {
                    PleosButton(
                        text = "차량 속도 접근 허용",
                        onClick = onRequestSpeedPermission,
                        enabled = uiState.canOpenBadges,
                    )
                }
                if (uiState.vehicle.motion.availability != MotionAvailability.Available) {
                    PleosButton(text = "차량 상태 다시 확인", onClick = onRetryVehicle, enabled = uiState.canOpenBadges)
                }
                PleosButton(text = "컬렉션 보기", onClick = onOpenBadges, enabled = uiState.canOpenBadges)
                Text("출발하면 주행을 시작하고, 멈추면 주행 중 멈춤으로 표시하며, 정지 후 P 기어에서 종료합니다.")
            }
        }
    }
}
