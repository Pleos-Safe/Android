package io.issac.pleosandroid.feature.home.journey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosButtonStyle
import io.issac.pleosandroid.core.designsystem.component.PleosDialog
import io.issac.pleosandroid.core.designsystem.component.PleosInfoPanel
import io.issac.pleosandroid.core.designsystem.component.PleosScreen
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.feature.home.JourneyProgressDisplay

@Composable
internal fun ParkedJourneyPause(
    progress: JourneyProgressDisplay?,
    recovering: Boolean,
    pausedAtNanos: Long?,
    busy: Boolean,
    error: Boolean,
    onContinueJourney: () -> Unit,
    onFinishJourney: () -> Unit,
    modifier: Modifier = Modifier,
    visitReward: @Composable () -> Unit = {},
) {
    var confirming by rememberSaveable(pausedAtNanos) { mutableStateOf(false) }
    var readyToResume by rememberSaveable(pausedAtNanos) { mutableStateOf(false) }
    val colors = PleosTheme.colors
    PleosScreen(title = if (recovering) "이전 주행 이어가기" else "잠시 주차했어요", modifier = modifier) {
        ParkedJourneyColumns(
            primaryWeight = 1.1f,
            primary = {
                Column(verticalArrangement = Arrangement.spacedBy(PleosLayout.itemGap)) {
                    PleosInfoPanel(Modifier.fillMaxWidth()) {
                        Text(
                            if (recovering) "지난 주행 기록이 남아 있어요" else "휴식 중에도 기록을 보관해요",
                            style = PleosTextStyles.section,
                            color = colors.textPrimary,
                        )
                        Text(
                            if (readyToResume) "다시 출발하면 이어서 기록해요." else "쉬었다가 이어서 달리거나 여기서 주행을 마칠 수 있어요.",
                            style = PleosTextStyles.body,
                            color = colors.textSecondary,
                        )
                    }
                    if (!recovering) visitReward()
                }
            },
            side = {
                Column(verticalArrangement = Arrangement.spacedBy(PleosLayout.itemGap)) {
                    if (progress != null) {
                        PleosInfoPanel(Modifier.fillMaxWidth()) {
                            JourneyProgressRows(progress)
                        }
                    }
                    if (error) {
                        Text("처리하지 못했어요. 차량 상태를 확인하고 다시 시도해 주세요.", color = colors.statusPending)
                    }
                    PleosButton(
                        "주행 계속하기",
                        onClick = {
                            if (recovering) {
                                onContinueJourney()
                            } else {
                                readyToResume = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && !readyToResume,
                        showArrow = true,
                    )
                    PleosButton(
                        "주행 종료하기",
                        onClick = { confirming = true },
                        modifier = Modifier.fillMaxWidth(),
                        style = PleosButtonStyle.Secondary,
                        enabled = !busy,
                        showArrow = true,
                    )
                }
            },
        )
    }
    if (confirming) {
        PleosDialog(
            title = "이번 주행을 마무리할까요?",
            message = "기록된 주행 거리와 실제 주행 시간을 바탕으로 강화 포인트를 정산해요.",
            confirmLabel = "주행 종료하기",
            dismissLabel = "계속 기록하기",
            onConfirm = {
                confirming = false
                onFinishJourney()
            },
            onDismissRequest = { confirming = false },
        )
    }
}
