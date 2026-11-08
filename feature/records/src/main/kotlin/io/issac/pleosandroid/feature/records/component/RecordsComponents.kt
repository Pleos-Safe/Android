package io.issac.pleosandroid.feature.records.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.FullLayoutMinWidth
import io.issac.pleosandroid.core.designsystem.component.pleosShellBackground
import io.issac.pleosandroid.core.designsystem.modifier.customClickable
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosShape
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun RecordsPage(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.(Boolean) -> Unit,
) {
    val scale = PleosTextStyles.scale
    BoxWithConstraints(modifier.fillMaxSize().pleosShellBackground()) {
        val wide = maxWidth >= FullLayoutMinWidth * scale
        val margin = PleosLayout.horizontalPadding(wide)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
                horizontal = margin,
                vertical =
                    24.dp * scale,
            ),
            verticalArrangement = Arrangement.spacedBy(PleosLayout.sectionGap),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp * scale)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PleosLayout.itemGap),
                ) {
                    if (onBack != null) {
                        RecordIcon(
                            RecordSymbol.Back,
                            Modifier.size(48.dp * scale).semantics { contentDescription = "뒤로가기" }
                                .customClickable(role = Role.Button, onClick = onBack).padding(10.dp * scale),
                        )
                    }
                    Text(
                        title,
                        modifier = Modifier.weight(1f),
                        style = if (wide) PleosTextStyles.titleFull else PleosTextStyles.titleSplit,
                        color = PleosTheme.colors.textPrimary,
                    )
                }
                if (subtitle != null) RecordCaption(subtitle)
            }
            content(wide)
        }
    }
}

@Composable
internal fun RecordColumns(wide: Boolean, leading: @Composable () -> Unit, trailing: @Composable () -> Unit) {
    val gap = PleosLayout.columnGap
    if (wide) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Column(Modifier.weight(.44f)) { leading() }
            Column(Modifier.weight(.56f)) { trailing() }
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp * PleosTextStyles.scale)) {
            leading()
            trailing()
        }
    }
}

@Composable
internal fun QuietPlate(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PleosTheme.colors
    Column(
        modifier.fillMaxWidth().background(colors.surface, PleosShape.inset)
            .border(1.dp, colors.borderDefault, PleosShape.inset).padding(contentPadding * PleosTextStyles.scale),
        verticalArrangement = Arrangement.spacedBy(20.dp * PleosTextStyles.scale),
        content = content,
    )
}

@Composable
internal fun RecordCaption(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Text(
        text,
        modifier,
        style = PleosTextStyles.meta,
        color = if (accent) PleosTheme.colors.actionPrimary else PleosTheme.colors.textSecondary,
    )
}

@Composable
internal fun RecordHeading(text: String) {
    Text(text, style = PleosTextStyles.section, color = PleosTheme.colors.textPrimary)
}

@Composable
internal fun RecordBody(text: String) {
    Text(text, style = PleosTextStyles.body, color = PleosTheme.colors.textSecondary)
}

@Composable
internal fun RecordDivider() = HorizontalDivider(color = PleosTheme.colors.borderDefault)

@Composable
internal fun ObservationRow(title: String, detail: String, value: String? = null, onClick: (() -> Unit)? = null) {
    val modifier = if (onClick == null) Modifier else Modifier.customClickable(role = Role.Button, onClick = onClick)
    Column(modifier.fillMaxWidth()) {
        RecordDivider()
        Column(
            Modifier.fillMaxWidth().heightIn(min = 96.dp * PleosTextStyles.scale).padding(
                vertical =
                    20.dp * PleosTextStyles.scale,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp * PleosTextStyles.scale),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, Modifier.weight(1f), style = PleosTextStyles.body, color = PleosTheme.colors.textPrimary)
                if (value != null) RecordCaption(value, accent = true)
            }
            if (detail.isNotBlank()) RecordCaption(detail)
        }
    }
}

internal enum class RecordSymbol(val path: String) {
    Shield("M12 1L22 5V13Q22 20 12 24Q2 20 2 13V5ZM7 12L11 16L18 8"),
    Bookmark("M5 2H19V23L12 18L5 23Z"),
    Back("M16 3L7 12L16 21M7 12H22"),
    Pause("M8 3V21M16 3V21"),
    Check("M4 12L9 17L21 4"),
}

@Composable
internal fun RecordIcon(symbol: RecordSymbol, modifier: Modifier = Modifier) {
    val path = remember(symbol) { PathParser().parsePathString(symbol.path).toPath() }
    val tint = PleosTheme.colors.actionPrimary
    Canvas(modifier.size(32.dp * PleosTextStyles.scale)) {
        withTransform({ scale(size.width / 24, size.height / 24, Offset.Zero) }) {
            drawPath(path, tint, style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

internal fun JourneyRecord.dateLabel(): String = DateTimeFormatter.ofPattern("yyyy. MM. dd", Locale.KOREAN)
    .format(endedAt.atZone(ZoneId.of(zoneId)))

internal fun JourneyRecord.timeLabel(): String {
    val format = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN).withZone(ZoneId.of(zoneId))
    return "${format.format(startedAt)} — ${format.format(endedAt)}"
}

internal fun JourneyRecord.distanceLabel(): String = String.format(Locale.KOREAN, "%.1f km", distanceMeters / 1_000.0)
internal fun JourneyRecord.durationLabel(): String =
    (driving?.elapsedMillis ?: Duration.between(startedAt, endedAt).toMillis()).recordDurationLabel()

internal fun Long.recordDurationLabel(): String {
    val seconds = coerceAtLeast(0L) / 1_000
    return when {
        seconds >= 3_600 -> "${seconds / 3_600}시간 ${(seconds / 60) % 60}분"
        seconds >= 60 -> "${seconds / 60}분 ${seconds % 60}초"
        else -> "${seconds}초"
    }
}
