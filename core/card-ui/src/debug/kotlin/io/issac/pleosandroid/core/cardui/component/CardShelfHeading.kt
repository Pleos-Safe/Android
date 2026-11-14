package io.issac.pleosandroid.core.cardui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout

@Composable
internal fun CardShelfHeading(title: String, wide: Boolean, balance: String?, onPreviewMotions: () -> Unit) {
    val previewLink: @Composable () -> Unit = {
        PleosTextLink("연출 모아보기", onPreviewMotions, Modifier.testTag("open-motion-gallery"))
    }
    if (wide) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PleosLayout.itemGap),
        ) {
            Box(Modifier.weight(1f)) { AlbumHeading(title, wide, balance) }
            previewLink()
        }
    } else {
        Column {
            AlbumHeading(title, wide, balance)
            previewLink()
        }
    }
}
