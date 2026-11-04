package io.issac.pleosandroid.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

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
fun PleosTheme(
    colors: PleosColorScheme = DarkColors,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalPleosColors provides colors) {
        MaterialTheme(
            colorScheme = remember(colors) { colors.toMaterialColorScheme() },
            typography = PleosTypography,
            shapes = PleosShapes,
            content = content,
        )
    }
}
