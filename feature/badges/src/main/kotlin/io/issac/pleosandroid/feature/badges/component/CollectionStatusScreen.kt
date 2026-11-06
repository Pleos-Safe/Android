package io.issac.pleosandroid.feature.badges.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.designsystem.component.PleosButton

@Composable
internal fun CollectionStatusScreen(
    message: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
) {
    CollectionPage(modifier) { window ->
        if (onBack != null) CollectionBack("돌아가기", onBack)
        CollectionTitle(message, window.full)
        if (onRetry != null) PleosButton("다시 시도", onRetry)
    }
}
