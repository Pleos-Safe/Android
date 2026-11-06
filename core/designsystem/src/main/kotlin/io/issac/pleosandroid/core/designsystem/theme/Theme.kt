package io.issac.pleosandroid.core.designsystem.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val DarkColors = PleosColorScheme()
private val LocalPleosColors = staticCompositionLocalOf { DarkColors }

object PleosTheme {
    val colors: PleosColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalPleosColors.current
}

/** App tokens are the source of truth; Material components receive an adapter. */
@Composable
fun PleosTheme(colors: PleosColorScheme = DarkColors, content: @Composable () -> Unit) {
    ProvidePleosTheme(colors, scale = 1f, content)
}

/** Measure the activity or host pane once. Dialogs and toolbars inherit the same reading size. */
@Composable
fun PleosWindowTheme(
    modifier: Modifier = Modifier,
    colors: PleosColorScheme = DarkColors,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize(), propagateMinConstraints = true) {
        // The 520 × 760 design is the smallest viewport for the base ramp.
        val scale = minOf(maxWidth / 520.dp, maxHeight / 760.dp).coerceIn(1f, 1.5f)
        ProvidePleosTheme(colors, scale, content)
    }
}

@Composable
private fun ProvidePleosTheme(colors: PleosColorScheme, scale: Float, content: @Composable () -> Unit) {
    val type = remember(scale) { PleosTypeScale(scale) }
    CompositionLocalProvider(LocalPleosColors provides colors, LocalPleosTypeScale provides type) {
        MaterialTheme(
            colorScheme = remember(colors) { colors.toMaterialColorScheme() },
            typography = remember(type) { type.asMaterialTypography() },
            shapes = PleosShapes,
            content = content,
        )
    }
}
