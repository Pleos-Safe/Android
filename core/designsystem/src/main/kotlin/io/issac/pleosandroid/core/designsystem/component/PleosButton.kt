package io.issac.pleosandroid.core.designsystem.component

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.designsystem.theme.PleosShape
import io.issac.pleosandroid.core.designsystem.theme.PleosSpacing
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme

@Composable
fun PleosButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = PleosTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = PleosSpacing.space56),
        enabled = enabled,
        shape = PleosShape.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.actionPrimary,
            contentColor = colors.textOnAction,
            disabledContainerColor = colors.actionDisabled,
            disabledContentColor = colors.textDisabled,
        ),
    ) {
        Text(text)
    }
}
