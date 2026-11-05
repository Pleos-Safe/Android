package io.issac.pleosandroid.feature.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

internal object CollectionColors {
    val Background = Color(0xFF0B1220)
    val Surface = Color(0xFF151F30)
    val Border = Color(0xFF334458)
    val Text = Color(0xFFF0F5FC)
    val Muted = Color(0xFFBAC8D8)
    val Accent = Color(0xFF9DEBDD)
}

@Composable
internal fun CollectionPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CollectionColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.collection_back), color = CollectionColors.Accent)
        }
        Text(title, color = CollectionColors.Text, style = MaterialTheme.typography.headlineLarge)
        content()
    }
}
