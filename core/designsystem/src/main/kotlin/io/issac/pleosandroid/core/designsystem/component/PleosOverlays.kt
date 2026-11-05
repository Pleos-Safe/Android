package io.issac.pleosandroid.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.issac.pleosandroid.core.designsystem.theme.PleosCorners
import io.issac.pleosandroid.core.designsystem.theme.PleosSpacing
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme

@Composable
fun PleosInfoPanel(
    modifier: Modifier = Modifier,
    outline: Color = PleosTheme.colors.borderDefault,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = CutCornerShape(topStart = PleosCorners.insetCut, bottomEnd = PleosCorners.insetCut)
    Column(
        modifier.clip(shape)
            .background(PleosTheme.colors.surface)
            .border(1.dp, outline, shape)
            .padding(PleosSpacing.space24),
        verticalArrangement = Arrangement.spacedBy(PleosSpacing.space8),
        content = content,
    )
}

/** The caller owns visibility and decides when a confirmed action dismisses the dialog. */
@Composable
fun PleosDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    confirmStyle: PleosButtonStyle = PleosButtonStyle.Primary,
    confirmEnabled: Boolean = true,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        PleosInfoPanel(modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PleosSpacing.space16),
            ) {
                Text(
                    title,
                    Modifier.semantics {
                        heading()
                    },
                    style = PleosTextStyles.section,
                    color = PleosTheme.colors.textPrimary,
                )
                Text(message, style = PleosTextStyles.body, color = PleosTheme.colors.textSecondary)
                PleosButton(
                    confirmLabel,
                    onConfirm,
                    Modifier.fillMaxWidth(),
                    enabled = confirmEnabled,
                    style = confirmStyle,
                    showArrow = true,
                )
                if (dismissLabel != null) {
                    PleosButton(
                        dismissLabel,
                        onDismissRequest,
                        Modifier.fillMaxWidth(),
                        style = PleosButtonStyle.Secondary,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PleosBottomSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = CutCornerShape(topStart = PleosCorners.stageCut, topEnd = PleosCorners.stageCut),
        containerColor = PleosTheme.colors.surface,
        contentColor = PleosTheme.colors.textPrimary,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(
                start = PleosSpacing.space24,
                end = PleosSpacing.space24,
                bottom = PleosSpacing.space24,
            ),
            verticalArrangement = Arrangement.spacedBy(PleosSpacing.space12),
        ) {
            Text(title, Modifier.semantics { heading() }, style = PleosTextStyles.section)
            content()
        }
    }
}
