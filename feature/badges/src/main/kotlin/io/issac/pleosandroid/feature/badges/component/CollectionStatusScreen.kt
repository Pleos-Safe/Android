package io.issac.pleosandroid.feature.badges.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.designsystem.component.FullLayoutMinWidth
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles

@Composable
internal fun CollectionStatusScreen(
    message: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= FullLayoutMinWidth * PleosTextStyles.scale
        Column(
            Modifier.padding(PleosLayout.horizontalPadding(wide)),
            verticalArrangement = Arrangement.spacedBy(PleosLayout.sectionGap),
        ) {
            if (onBack != null) CollectionBack("돌아가기", onBack)
            CollectionTitle(message, wide)
            if (onRetry != null) PleosButton("다시 시도", onRetry)
        }
    }
}
