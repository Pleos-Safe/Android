package io.issac.pleosandroid.feature.main.map

import android.content.res.Resources
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.theme.PleosMotion
import io.issac.pleosandroid.core.designsystem.theme.PleosSpacing
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.feature.main.R
import org.json.JSONObject

@Composable
internal fun DrivingRegionMap(
    position: PlaceMapPosition?,
    badgeRegionIds: Set<String>,
    isMoving: Boolean,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val regions = remember(resources) { loadDrivingRegions(resources) }
    val colors = PleosTheme.colors
    val target = position?.takeIf(KoreaRegionProjection::contains)?.let(KoreaRegionProjection::project)
    val marker = if (isMoving && target != null) {
        // Compose applies the system animation duration scale to this transition.
        val animated by animateOffsetAsState(
            targetValue = target,
            animationSpec = tween(PleosMotion.NAVIGATION_DURATION_MILLIS, easing = PleosMotion.navigationEasing),
            label = "driving-position",
        )
        animated
    } else {
        target
    }
    val locationLabel = stringResource(
        when {
            position == null -> R.string.driving_region_map_location_unavailable
            target == null -> R.string.driving_region_map_location_outside
            else -> R.string.driving_region_map_current_position
        },
    )
    val title = stringResource(R.string.driving_region_map_title)
    val description = stringResource(R.string.driving_region_map_description, locationLabel)

    Column(
        modifier = modifier.background(colors.background).padding(PleosSpacing.space16),
        verticalArrangement = Arrangement.spacedBy(PleosSpacing.space8),
    ) {
        Text(title, color = colors.textSecondary, style = PleosTextStyles.meta)
        Canvas(
            Modifier.fillMaxWidth().weight(1f)
                .semantics { contentDescription = description },
        ) {
            val padding = 14.dp.toPx()
            val scale = minOf(
                (size.width - padding * 2) / KoreaRegionProjection.width,
                (size.height - padding * 2) / KoreaRegionProjection.height,
            )
            if (scale <= 0) return@Canvas

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(colors.actionSecondary.copy(alpha = 0.60f), Color.Transparent),
                    center = center,
                    radius = size.maxDimension * 0.55f,
                ),
            )
            val gridColor = colors.borderDefault.copy(alpha = 0.34f)
            for (division in 1..3) {
                val x = size.width * division / 4
                val y = size.height * division / 4
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }

            val mapOrigin = Offset(
                (size.width - KoreaRegionProjection.width * scale) / 2,
                (size.height - KoreaRegionProjection.height * scale) / 2,
            )
            withTransform({
                translate(mapOrigin.x, mapOrigin.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                for (region in regions) {
                    val earned = region.id in badgeRegionIds
                    val fill = if (earned) colors.accentLilac.copy(alpha = 0.60f) else colors.actionSecondary
                    drawPath(region.boundary, fill)
                }
                for (region in regions) {
                    val earned = region.id in badgeRegionIds
                    val edge = if (earned) colors.accentLilac else colors.borderControl
                    val width = if (earned) 1.5.dp else 1.dp
                    drawPath(region.boundary, edge, style = Stroke(width.toPx() / scale))
                }
            }
            marker?.let { point ->
                val pixel = mapOrigin + point * scale
                drawCircle(colors.actionPrimary.copy(alpha = 0.19f), 13.dp.toPx(), pixel)
                drawCircle(colors.background, 7.dp.toPx(), pixel)
                drawCircle(colors.actionPrimary, 5.dp.toPx(), pixel)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(PleosSpacing.space16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DrivingMapLegend(
                label = locationLabel,
                color = if (target != null) colors.actionPrimary else colors.textDisabled,
                modifier = Modifier.weight(1f, fill = false),
            )
            DrivingMapLegend(
                label = stringResource(R.string.driving_region_map_owned_badge_area),
                color = colors.accentLilac,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun DrivingMapLegend(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PleosSpacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(
            text = label,
            color = PleosTheme.colors.textSecondary,
            style = PleosTextStyles.meta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class DrivingRegion(val id: String, val boundary: Path)

private fun loadDrivingRegions(resources: Resources): List<DrivingRegion> {
    val json = resources.openRawResource(R.raw.driving_korea_regions).bufferedReader().use { it.readText() }
    val regions = JSONObject(json).getJSONArray("regions")
    return List(regions.length()) { index ->
        val region = regions.getJSONObject(index)
        val polygons = region.getJSONArray("polygons")
        val boundary = Path().apply {
            fillType = PathFillType.EvenOdd
            for (polygonIndex in 0 until polygons.length()) {
                val polygon = polygons.getJSONArray(polygonIndex)
                for (ringIndex in 0 until polygon.length()) {
                    val ring = polygon.getJSONArray(ringIndex)
                    for (pointIndex in 0 until ring.length()) {
                        val coordinate = ring.getJSONArray(pointIndex)
                        val point = KoreaRegionProjection.project(
                            PlaceMapPosition(latitude = coordinate.getDouble(1), longitude = coordinate.getDouble(0)),
                        )
                        if (pointIndex == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                    }
                    close()
                }
            }
        }
        DrivingRegion(region.getString("id"), boundary)
    }
}

private object KoreaRegionProjection {
    private const val WEST = 124.5
    private const val EAST = 131.0
    private const val SOUTH = 33.1
    private const val NORTH = 38.7

    // Equirectangular projection with a standard parallel near the center of the map (36° N).
    private const val LONGITUDE_SCALE = 0.809016994
    val width = ((EAST - WEST) * LONGITUDE_SCALE).toFloat()
    val height = (NORTH - SOUTH).toFloat()

    fun contains(position: PlaceMapPosition): Boolean =
        position.longitude in WEST..EAST && position.latitude in SOUTH..NORTH

    fun project(position: PlaceMapPosition): Offset =
        Offset(((position.longitude - WEST) * LONGITUDE_SCALE).toFloat(), (NORTH - position.latitude).toFloat())
}
