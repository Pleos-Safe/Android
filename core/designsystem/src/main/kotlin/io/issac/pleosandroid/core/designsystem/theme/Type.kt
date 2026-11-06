package io.issac.pleosandroid.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
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

/** Foundation sizes refer to a 760-high design window. Card artwork scales inside its own print. */
@Immutable
class PleosTypeScale internal constructor(val scale: Float) {
    private fun role(size: Int, line: Int, weight: FontWeight) = TextStyle(
        fontFamily = PleosFontFamily,
        fontWeight = weight,
        fontSize = (size * scale).sp,
        lineHeight = (line * scale).sp,
        letterSpacing = 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

    val titleFull = role(30, 40, FontWeight.Medium)
    val titleSplit = role(28, 36, FontWeight.Medium)
    val section = role(22, 32, FontWeight.Medium)
    val body = role(18, 28, FontWeight.Normal)
    val meta = role(16, 24, FontWeight.Normal)
}

internal val LocalPleosTypeScale = staticCompositionLocalOf { PleosTypeScale(1f) }

val PleosTextStyles: PleosTypeScale
    @Composable
    @ReadOnlyComposable
    get() = LocalPleosTypeScale.current

// Adapt Material roles to the five Foundation styles without introducing another type scale.
internal fun PleosTypeScale.asMaterialTypography() = Typography(
    displayLarge = titleFull,
    displayMedium = titleFull,
    displaySmall = titleFull,
    headlineLarge = titleFull,
    headlineMedium = titleSplit,
    headlineSmall = section,
    titleLarge = section,
    titleMedium = body,
    titleSmall = meta,
    bodyLarge = body,
    bodyMedium = body,
    bodySmall = meta,
    labelLarge = body,
    labelMedium = meta,
    labelSmall = meta,
)
