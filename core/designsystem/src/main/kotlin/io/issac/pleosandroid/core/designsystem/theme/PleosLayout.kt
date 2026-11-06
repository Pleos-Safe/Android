package io.issac.pleosandroid.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Space for the current reading size, independent of the card's printed artwork. */
object PleosLayout {
    val itemGap: Dp
        @Composable @ReadOnlyComposable
        get() = 12.dp * PleosTextStyles.scale
    val sectionGap: Dp
        @Composable @ReadOnlyComposable
        get() = 24.dp * PleosTextStyles.scale
    val columnGap: Dp
        @Composable @ReadOnlyComposable
        get() = 32.dp * PleosTextStyles.scale
    val panelPadding: Dp
        @Composable @ReadOnlyComposable
        get() = 24.dp * PleosTextStyles.scale
    val twoPaneMinWidth: Dp
        @Composable @ReadOnlyComposable
        get() = 760.dp * PleosTextStyles.scale

    @Composable
    @ReadOnlyComposable
    fun horizontalPadding(wide: Boolean): Dp =
        (if (wide) PleosSpacing.contentHorizontalFull else PleosSpacing.contentHorizontalSplit) * PleosTextStyles.scale
}
