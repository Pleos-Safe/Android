package io.issac.pleosandroid.feature.settings

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
internal fun SettingsScreen(
    onRecordingSettings: () -> Unit,
    onMembershipSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PleosScreen("설정", modifier) {
        PleosBackLink("돌아가기", onBack)
        PleosInfoPanel(Modifier.widthIn(max = 760.dp * PleosTextStyles.scale).fillMaxWidth()) {
            PleosButton(
                "PLUS 구독 관리",
                onMembershipSettings,
                Modifier.fillMaxWidth(),
                style = PleosButtonStyle.Secondary,
                showArrow = true,
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
