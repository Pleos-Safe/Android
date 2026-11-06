package io.issac.pleosandroid.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.R
import io.issac.pleosandroid.core.designsystem.theme.PleosShape
import io.issac.pleosandroid.core.designsystem.theme.PleosSpacing
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme

@Composable
fun PleosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    onSubmit: ((String) -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val scale = PleosTextStyles.scale
    val colors = PleosTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics { contentDescription = label },
        enabled = enabled,
        singleLine = true,
        textStyle = PleosTextStyles.body.copy(color = if (enabled) colors.textPrimary else colors.textDisabled),
        cursorBrush = SolidColor(colors.actionPrimary),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = if (onSubmit == null) KeyboardActions.Default else KeyboardActions { onSubmit(value) },
        decorationBox = { field ->
            Row(
                Modifier
                    .background(if (enabled) colors.actionSecondary else colors.actionDisabled, PleosShape.button)
                    .border(1.dp, colors.borderControl, PleosShape.button)
                    .heightIn(min = PleosSpacing.space56 * scale)
                    .padding(horizontal = PleosSpacing.space16 * scale, vertical = PleosSpacing.space8 * scale),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PleosSpacing.space12 * scale),
            ) {
                leadingIcon?.invoke()
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = PleosTextStyles.body,
                            color = if (enabled) colors.textSecondary else colors.textDisabled,
                        )
                    }
                    field()
                }
                trailingIcon?.invoke()
            }
        },
    )
}

@Composable
fun PleosSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = stringResource(R.string.pleos_search_hint),
) {
    val colors = PleosTheme.colors
    PleosTextField(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(R.string.pleos_place_search),
        modifier = modifier,
        placeholder = placeholder,
        enabled = enabled,
        imeAction = ImeAction.Search,
        onSubmit = onSearch,
        leadingIcon = {
            PleosGlyph(
                PleosSymbol.Search,
                if (enabled) colors.actionPrimary else colors.textDisabled,
                Modifier.size(PleosSpacing.space24 * PleosTextStyles.scale),
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                PleosIconButton(
                    symbol = PleosSymbol.Close,
                    label = stringResource(R.string.pleos_clear_query),
                    onClick = { onValueChange("") },
                    enabled = enabled,
                )
            }
        },
    )
}
