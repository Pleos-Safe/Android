package io.issac.pleosandroid.feature.home.journey

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosButtonStyle
import io.issac.pleosandroid.core.designsystem.component.PleosDialog
import io.issac.pleosandroid.core.designsystem.component.PleosInfoPanel
import io.issac.pleosandroid.core.designsystem.component.PleosScreen
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosShape
import io.issac.pleosandroid.core.designsystem.theme.PleosSpacing
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.driving.repository.RecordingPhase
import io.issac.pleosandroid.core.domain.points.formatHalfPoints
import io.issac.pleosandroid.feature.home.HomePlace
import io.issac.pleosandroid.feature.home.JourneyProgressDisplay
import io.issac.pleosandroid.feature.home.JourneySummary
import io.issac.pleosandroid.feature.home.LocalHomeCard
import io.issac.pleosandroid.feature.home.R

@Composable
internal fun ParkedTravelHome(
    place: HomePlace? = null,
    onOpenBadges: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenMap: (() -> Unit)? = null,
    onOpenRecords: () -> Unit = {},
    pointBalance: Long? = null,
    demoWallet: Boolean = false,
) {
    val colors = PleosTheme.colors
    PleosScreen(title = stringResource(R.string.journey_home_title), modifier = modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.journey_home_recommendation),
                style = PleosTextStyles.meta,
                color = colors.actionPrimary,
            )
            Text(
                text = stringResource(R.string.journey_before_driving),
                style = PleosTextStyles.meta,
                color = colors.textSecondary,
            )
        }
        ParkedJourneyColumns(
            primaryWeight = 1.35f,
            primary = { wide ->
                Column(verticalArrangement = Arrangement.spacedBy(PleosLayout.itemGap)) {
                    RecommendedPlacePhoto(wide, place)
                    Column(verticalArrangement = Arrangement.spacedBy(PleosSpacing.space4 * PleosTextStyles.scale)) {
                        Text(
                            text = place?.name ?: "여행지를 불러오고 있어요",
                            style = PleosTextStyles.section,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = place?.district.orEmpty(),
                            style = PleosTextStyles.meta,
                            color = colors.textSecondary,
                        )
                    }
                    if (onOpenMap != null) {
                        PleosButton(
                            text = stringResource(R.string.journey_open_map),
                            onClick = onOpenMap,
                            modifier = Modifier.fillMaxWidth(),
                            showArrow = true,
                        )
                    }
                }
            },
            side = { _ ->
                PleosInfoPanel(Modifier.fillMaxWidth(), outline = colors.borderControl) {
                    Text(
                        text = stringResource(R.string.journey_home_collection_title),
                        style = PleosTextStyles.section,
                        color = colors.textPrimary,
                    )
                    if (pointBalance != null) {
                        Text(
                            if (demoWallet) "체험용 강화 포인트" else "보유 강화 포인트",
                            style = PleosTextStyles.meta,
                            color = colors.textSecondary,
                        )
                        Text(
                            pointsLabel(pointBalance),
                            style = PleosTextStyles.titleSplit,
                            color = colors.actionPrimary,
                        )
                    }
                    LocalHomeCard.current()
                    PleosButton(
                        stringResource(R.string.journey_collection),
                        onOpenBadges,
                        Modifier.fillMaxWidth(),
                        style = PleosButtonStyle.Secondary,
                        showArrow = true,
                    )
                    PleosTextLink("운전습관 기록", onOpenRecords)
                }
            },
        )
    }
}

@Composable
private fun RecommendedPlacePhoto(wide: Boolean, place: HomePlace?) {
    val context = LocalContext.current
    val imageId = remember(place?.imageKey, context) {
        place?.imageKey?.let { context.resources.getIdentifier(it, "drawable", context.packageName) } ?: 0
    }
    val colors = PleosTheme.colors
    val shape = PleosShape.button
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(if (wide) 1.7f else 2.05f).clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.borderControl, shape),
    ) {
        if (imageId != 0) {
            Image(
                painter = painterResource(imageId),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(horizontalBias = 0f, verticalBias = -0.35f),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(androidx.compose.ui.graphics.Color.Transparent, colors.background.copy(alpha = .65f)),
                ),
            ),
        )
        Text(
            text = place?.category.orEmpty(),
            modifier = Modifier.align(Alignment.BottomStart).padding(PleosLayout.panelPadding)
                .background(colors.background.copy(alpha = .88f), PleosShape.chip)
                .padding(horizontal = PleosSpacing.space12, vertical = PleosSpacing.space4),
            style = PleosTextStyles.meta,
            color = colors.textPrimary,
        )
    }
}
