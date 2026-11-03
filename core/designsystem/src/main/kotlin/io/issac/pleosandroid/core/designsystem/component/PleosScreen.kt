package io.issac.pleosandroid.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.designsystem.theme.PleosSpacing

/** Shared scrollable screen shell. Scaffold insets are supplied by the caller's modifier. */
@Composable
fun PleosScreen(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PleosSpacing.large),
        verticalArrangement = Arrangement.spacedBy(PleosSpacing.medium),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        content()
    }
}
