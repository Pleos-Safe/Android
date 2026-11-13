package io.issac.pleosandroid.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/** Hyundai CI reference colors and derived dark vehicle tones; UI consumes semantic aliases. */
internal object PleosReferenceColors {
    // Official reference: https://www.hyundai.com/kr/ko/info/ci/ci-color
    val hyundaiBlue = Color(0xFF002C5F)
    val activeBlue = Color(0xFF00AAD2)
    val skyBlue = Color(0xFFAACAE6)
    val white = Color(0xFFFFFFFF)
    val lightSand = Color(0xFFF6F3F2)

    // App-derived tones, not additional official CI colors. Backgrounds mix Hyundai Blue with black;
    // muted text and borders blend the blue references for separation on the dark vehicle surface.
    val backgroundBlueBlack = Color(0xFF000F21)
    val shellBlueBlack = Color(0xFF000B18)
    val shellBlue = Color(0xFF001A39)
    val surfaceBlue = Color(0xFF001630)
    val secondaryBlue = Color(0xFF00234C)
    val disabledBlue = Color(0xFF001F43)
    val chipBlue = Color(0xFF001834)
    val mutedBlueText = Color(0xFF7795B1)
    val borderBlue = Color(0xFF294B70)
    val controlBorderBlue = Color(0xFF527699)

    // Feature meaning is independent of branding: visit progress and warning signals retain color.
    val visitPurple = Color(0xFFB6A7F4)
    val pendingAmber = Color(0xFFEDCBA1)
    val attentionRose = Color(0xFFE7B2C4)
}
