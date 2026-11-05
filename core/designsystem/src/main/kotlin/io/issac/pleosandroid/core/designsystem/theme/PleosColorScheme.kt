package io.issac.pleosandroid.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** PRISM/color semantic aliases, matching the Figma Dark mode. */
@Immutable
data class PleosColorScheme(
    val background: Color = PleosReferenceColors.navy950,
    val shellStart: Color = PleosReferenceColors.navy975,
    val shellEnd: Color = PleosReferenceColors.navy850,
    val surface: Color = PleosReferenceColors.navy900,
    val textPrimary: Color = PleosReferenceColors.ink100,
    val textSecondary: Color = PleosReferenceColors.ink300,
    val textDisabled: Color = PleosReferenceColors.ink500,
    val textOnAction: Color = PleosReferenceColors.navy950,
    val borderDefault: Color = PleosReferenceColors.line600,
    val borderControl: Color = PleosReferenceColors.line500,
    val actionPrimary: Color = PleosReferenceColors.mint200,
    val actionSecondary: Color = PleosReferenceColors.navy800,
    val actionDisabled: Color = PleosReferenceColors.navy820,
    val actionDanger: Color = PleosReferenceColors.rose300,
    val chipDefault: Color = PleosReferenceColors.navy880,
    val chipSelected: Color = PleosReferenceColors.navy700,
    val accentLilac: Color = PleosReferenceColors.lilac300,
    val statusPending: Color = PleosReferenceColors.amber300,
    val statusAttention: Color = PleosReferenceColors.rose300,
)
