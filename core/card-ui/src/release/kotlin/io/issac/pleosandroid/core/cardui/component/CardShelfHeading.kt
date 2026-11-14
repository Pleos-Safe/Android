package io.issac.pleosandroid.core.cardui.component

import androidx.compose.runtime.Composable

@Composable
internal fun CardShelfHeading(title: String, wide: Boolean, balance: String?, onPreviewMotions: () -> Unit) {
    AlbumHeading(title, wide, balance)
}
