package io.issac.pleosandroid.feature.home.driving

import android.content.res.Resources
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosMotion
import io.issac.pleosandroid.core.designsystem.theme.PleosSpacing
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.place.PlaceMapPosition
import io.issac.pleosandroid.feature.home.R
import org.json.JSONObject

@Composable
internal fun DrivingRegionMap(
    position: PlaceMapPosition?,
    regionVisitPackCounts: Map<String, Int>,
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
    val packDescription = regions.mapNotNull { region ->
        val count = regionVisitPackCounts[region.id]?.takeIf { it > 0 } ?: return@mapNotNull null
        resources.getString(
            R.string.driving_region_map_region_pack_description,
            region.name,
            count,
            VisitPackMapPalette.stepFor(count),
        )
    }.joinToString(", ").ifEmpty { resources.getString(R.string.driving_region_map_no_visit_packs) }
    val description = stringResource(R.string.driving_region_map_description, locationLabel, packDescription)

    Column(
        modifier = modifier.background(colors.background).padding(PleosLayout.panelPadding),
        verticalArrangement = Arrangement.spacedBy(PleosLayout.itemGap),
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
                    val count = regionVisitPackCounts[region.id] ?: 0
                    val fill = VisitPackMapPalette.colorFor(count, colors.actionSecondary)
                    drawPath(region.boundary, fill)
                }
                for (region in regions) {
                    val earned = (regionVisitPackCounts[region.id] ?: 0) > 0
                    val edge = if (earned) colors.visitedRegionOutline else colors.borderControl
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
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PleosSpacing.space16),
            verticalArrangement = Arrangement.spacedBy(PleosSpacing.space8),
        ) {
            DrivingMapLegend(
                label = locationLabel,
                color = if (target != null) colors.actionPrimary else colors.textDisabled,
            )
            DrivingMapLegend(
                label = stringResource(R.string.driving_region_map_visit_pack_scale),
                color = VisitPackMapPalette.colors.first(),
            )
        }
    }
}

@Composable
private fun DrivingMapLegend(label: String, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PleosSpacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(
            text = label,
            color = PleosTheme.colors.textSecondary,
            style = PleosTextStyles.meta,
            maxLines = 2,
        )
    }
}

private data class DrivingRegion(val id: String, val name: String, val boundary: Path)

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
        val id = region.getString("id")
        DrivingRegion(id, drivingRegionName(resources, id), boundary)
    }
}

private fun drivingRegionName(resources: Resources, id: String): String {
    val name = when (id) {
        "KR-11" -> R.string.driving_region_map_seoul
        "KR-26" -> R.string.driving_region_map_busan
        "KR-27" -> R.string.driving_region_map_daegu
        "KR-28" -> R.string.driving_region_map_incheon
        "KR-29" -> R.string.driving_region_map_gwangju
        "KR-30" -> R.string.driving_region_map_daejeon
        "KR-31" -> R.string.driving_region_map_ulsan
        "KR-41" -> R.string.driving_region_map_gyeonggi
        "KR-42" -> R.string.driving_region_map_gangwon
        "KR-43" -> R.string.driving_region_map_chungbuk
        "KR-44" -> R.string.driving_region_map_chungnam
        "KR-45" -> R.string.driving_region_map_jeonbuk
        "KR-46" -> R.string.driving_region_map_jeonnam
        "KR-47" -> R.string.driving_region_map_gyeongbuk
        "KR-48" -> R.string.driving_region_map_gyeongnam
        "KR-49" -> R.string.driving_region_map_jeju
        "KR-50" -> R.string.driving_region_map_sejong
        else -> return id
    }
    return resources.getString(name)
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
