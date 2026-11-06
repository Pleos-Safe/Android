package io.issac.pleosandroid.feature.main

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.PleosBackLink
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosButtonStyle
import io.issac.pleosandroid.core.designsystem.component.PleosInfoPanel
import io.issac.pleosandroid.core.designsystem.component.PleosScreen
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme

@Composable
internal fun SettingsScreen(onRecordingSettings: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PleosTheme.colors
    PleosScreen("설정", modifier) {
        PleosBackLink("돌아가기", onBack)
        PleosInfoPanel(Modifier.widthIn(max = 760.dp * PleosTextStyles.scale).fillMaxWidth()) {
            Text("나의 기록 방식", style = PleosTextStyles.section, color = colors.textPrimary)
            Text(
                "여정과 운전습관을 저장하는 방식을 선택해요.",
                style = PleosTextStyles.body,
                color = colors.textSecondary,
            )
            PleosButton(
                "여정 기록 저장",
                onRecordingSettings,
                Modifier.fillMaxWidth(),
                style = PleosButtonStyle.Secondary,
                showArrow = true,
            )
        }
    }
}
