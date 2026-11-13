package io.issac.pleosandroid.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Semantic roles for the blue vehicle theme. Card grades and feature signals keep their own colors. */
@Immutable
data class PleosColorScheme(
    val background: Color = PleosReferenceColors.backgroundBlueBlack,
    val shellStart: Color = PleosReferenceColors.shellBlueBlack,
    val shellEnd: Color = PleosReferenceColors.shellBlue,
    val surface: Color = PleosReferenceColors.surfaceBlue,
    val textPrimary: Color = PleosReferenceColors.white,
    val textSecondary: Color = PleosReferenceColors.skyBlue,
    val textDisabled: Color = PleosReferenceColors.mutedBlueText,
    val textOnAction: Color = PleosReferenceColors.hyundaiBlue,
    val borderDefault: Color = PleosReferenceColors.borderBlue,
    val borderControl: Color = PleosReferenceColors.controlBorderBlue,
    val actionPrimary: Color = PleosReferenceColors.activeBlue,
    val actionSecondary: Color = PleosReferenceColors.secondaryBlue,
    val actionDisabled: Color = PleosReferenceColors.disabledBlue,
    val actionDanger: Color = PleosReferenceColors.attentionRose,
    val chipDefault: Color = PleosReferenceColors.chipBlue,
    val chipSelected: Color = PleosReferenceColors.hyundaiBlue,
    val accentSecondary: Color = PleosReferenceColors.skyBlue,
    val accentHighlight: Color = PleosReferenceColors.lightSand,
    val visitedRegionOutline: Color = PleosReferenceColors.visitPurple,
    val statusPending: Color = PleosReferenceColors.pendingAmber,
    val statusAttention: Color = PleosReferenceColors.attentionRose,
)
