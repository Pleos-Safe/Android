package io.issac.pleosandroid.feature.badges

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosScreen

@Composable
fun BadgesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PleosScreen(title = "배지", modifier = modifier) {
        PleosButton(text = "메인으로 돌아가기", onClick = onBack)
    }
}
