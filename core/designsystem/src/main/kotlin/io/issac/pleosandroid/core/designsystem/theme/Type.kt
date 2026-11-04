package io.issac.pleosandroid.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.issac.pleosandroid.core.designsystem.R

// Bundled for offline use. Source: google/fonts, ofl/notosanskr (SIL OFL 1.1).
@OptIn(ExperimentalTextApi::class)
val PleosFontFamily = FontFamily(
    Font(
        resId = R.font.noto_sans_kr,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        resId = R.font.noto_sans_kr,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
)

/** Sizes, line heights and weights follow the Foundation specification labels. */
object PleosTextStyles {
    private val base = TextStyle(
        fontFamily = PleosFontFamily,
        letterSpacing = 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

    val titleFull = base.copy(fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium)
    val titleSplit = base.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium)
    val section = base.copy(fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium)
    val body = base.copy(fontSize = 18.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal)
    val meta = base.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)
}

// Adapt Material roles to the five Foundation styles without introducing another type scale.
internal val PleosTypography = Typography(
    displayLarge = PleosTextStyles.titleFull,
    displayMedium = PleosTextStyles.titleFull,
    displaySmall = PleosTextStyles.titleFull,
    headlineLarge = PleosTextStyles.titleFull,
    headlineMedium = PleosTextStyles.titleSplit,
    headlineSmall = PleosTextStyles.section,
    titleLarge = PleosTextStyles.section,
    titleMedium = PleosTextStyles.body,
    titleSmall = PleosTextStyles.meta,
    bodyLarge = PleosTextStyles.body,
    bodyMedium = PleosTextStyles.body,
    bodySmall = PleosTextStyles.meta,
    labelLarge = PleosTextStyles.body,
    labelMedium = PleosTextStyles.meta,
    labelSmall = PleosTextStyles.meta,
)
