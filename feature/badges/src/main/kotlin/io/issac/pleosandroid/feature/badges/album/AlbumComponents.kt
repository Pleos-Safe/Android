package io.issac.pleosandroid.feature.badges.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.PleosInfoPanel
import io.issac.pleosandroid.core.designsystem.component.pleosShellBackground
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.cards.ReceivedPack
import io.issac.pleosandroid.core.domain.points.formatHalfPoints
import io.issac.pleosandroid.feature.badges.component.Caption
import io.issac.pleosandroid.feature.badges.component.CollectionBack
import io.issac.pleosandroid.feature.badges.component.CollectionColumns
import io.issac.pleosandroid.feature.badges.component.CollectionTitle
import io.issac.pleosandroid.feature.badges.component.VaultStage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun points(half: Long): String = formatHalfPoints(half)
private val dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.KOREA)
internal fun dateOf(at: Instant): String = dateFormat.format(at.atZone(ZoneId.systemDefault()))
internal val ReceivedPack.title: String get() = if (id.startsWith("legacy:")) {
    "이전 소장 카드"
} else {
    packType?.packTitle ?: "소장 카드"
}

@Composable
internal fun AlbumHeading(title: String, wide: Boolean, note: String? = null, back: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (back != null) CollectionBack("뒤로", back)
        CollectionTitle(title, wide, Modifier.weight(1f))
        if (note != null) Caption(note)
    }
}

@Composable
internal fun BodyText(text: String) = Text(text, style = PleosTextStyles.body, color = PleosTheme.colors.textPrimary)

@Composable
internal fun AlbumPanel(content: @Composable ColumnScope.() -> Unit) {
    PleosInfoPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun ExhibitScreen(
    title: String,
    back: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    art: @Composable (Modifier) -> Unit,
    details: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().pleosShellBackground()) {
        val wide = maxWidth >= PleosLayout.twoPaneMinWidth
        val stageHeight = if (wide) {
            (maxHeight * .72f).coerceIn(235.dp, 790.dp)
        } else {
            (maxHeight * .46f).coerceIn(170.dp, 450.dp)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(
                rememberScrollState(),
            ).padding(horizontal = PleosLayout.horizontalPadding(wide), vertical = PleosLayout.itemGap),
            verticalArrangement = Arrangement.spacedBy(PleosLayout.itemGap),
        ) {
            AlbumHeading(title, wide, note, back)
            CollectionColumns(wide, first = {
                VaultStage(Modifier.fillMaxWidth().height(stageHeight)) {
                    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        art(Modifier.width(minOf(maxWidth * .84f, maxHeight * .61f)).aspectRatio(2f / 3f))
                    }
                }
            }, second = { AlbumPanel(details) })
        }
    }
}
