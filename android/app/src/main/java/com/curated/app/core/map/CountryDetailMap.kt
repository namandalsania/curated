package com.curated.app.core.map

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * One visited country up close: the country tinted, neighbors in the land
 * color for context, and a named dot for each city visited. Same flat style
 * and data as [VisitedWorldMap], just framed on one country.
 */
@Composable
fun CountryDetailMap(
    map: WorldMapData,
    visit: CountryVisit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium
    val labelColor = MaterialTheme.colorScheme.onSurface
    val landColor = MaterialTheme.colorScheme.outlineVariant
    val countryColor = MaterialTheme.colorScheme.secondary
    val dotColor = MaterialTheme.colorScheme.primary
    val haloColor = MaterialTheme.colorScheme.surface

    val cityPoints = remember(map, visit) {
        visit.cities.map { it to map.project(it.latitude, it.longitude) }
    }
    val focus = remember(map, visit) {
        focusBounds(
            map = map,
            rings = visit.countryCode?.let { map.outlines[it] }.orEmpty(),
            cityPoints = cityPoints.map { it.second },
            fallback = map.project(visit.latitude, visit.longitude)
        )
    }
    val description = if (visit.cities.isEmpty()) {
        "Map of ${visit.country}"
    } else {
        "Map of ${visit.country} with ${visit.cities.joinToString { it.name }}"
    }

    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .semantics { contentDescription = description }
            .drawWithCache {
                val padding = 20.dp.toPx()
                val scale = min((size.width - 2 * padding) / focus.width, (size.height - 2 * padding) / focus.height)
                val offset = Offset(size.width / 2 - focus.center.x * scale, size.height / 2 - focus.center.y * scale)
                val visibleGrid = Rect(
                    left = -offset.x / scale,
                    top = -offset.y / scale,
                    right = (size.width - offset.x) / scale,
                    bottom = (size.height - offset.y) / scale
                )

                val land = Path()
                val country = Path()
                for ((code, rings) in map.outlines) {
                    if (code == visit.countryCode) {
                        country.addRings(rings, scale, offset)
                    } else if (map.countryBounds[code]?.overlaps(visibleGrid) == true) {
                        land.addRings(rings, scale, offset)
                    }
                }

                val dotRadius = 5.dp.toPx()
                val dotRing = 2.dp.toPx()
                val labelGap = 4.dp.toPx()

                // Cities whose dots would overlap (Kyoto and Osaka on a map of
                // Japan) share one dot, labeled "Kyoto +1". Cities arrive sorted
                // by trips, so the one you went to most names the group.
                class DotGroup(val center: Offset, val names: MutableList<String>)
                val groups = mutableListOf<DotGroup>()
                for ((city, grid) in cityPoints) {
                    val point = grid * scale + offset
                    val near = groups.firstOrNull { (it.center - point).getDistance() < 2 * (dotRadius + dotRing) }
                    if (near != null) near.names += city.name else groups += DotGroup(point, mutableListOf(city.name))
                }
                val dots = groups.map { it.center }

                // Place each label right of its dot, else left; skip it if both
                // collide with another dot or label (the list below names them all).
                val taken = dots.mapTo(mutableListOf()) { Rect(it, dotRadius + dotRing) }
                val labels = mutableListOf<Pair<TextLayoutResult, Offset>>()
                groups.forEach { group ->
                    val dot = group.center
                    val text = if (group.names.size == 1) group.names[0] else "${group.names[0]} +${group.names.size - 1}"
                    val layout = textMeasurer.measure(text, labelStyle)
                    val labelSize = Size(layout.size.width.toFloat(), layout.size.height.toFloat())
                    val right = Rect(Offset(dot.x + dotRadius + labelGap, dot.y - labelSize.height / 2), labelSize)
                    val left = Rect(Offset(dot.x - dotRadius - labelGap - labelSize.width, dot.y - labelSize.height / 2), labelSize)
                    val spot = listOf(right, left).firstOrNull { candidate ->
                        candidate.left >= 0 && candidate.right <= size.width && taken.none { it.overlaps(candidate) }
                    }
                    if (spot != null) {
                        taken += spot
                        labels += layout to spot.topLeft
                    }
                }

                val border = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round)
                val halo = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round)

                onDrawBehind {
                    drawPath(land, landColor)
                    drawPath(land, haloColor, style = border)
                    drawPath(country, countryColor)
                    drawPath(country, haloColor, style = border)
                    for (dot in dots) {
                        drawCircle(haloColor, radius = dotRadius + dotRing, center = dot)
                        drawCircle(dotColor, radius = dotRadius, center = dot)
                    }
                    for ((layout, topLeft) in labels) {
                        drawText(layout, color = haloColor, topLeft = topLeft, drawStyle = halo)
                        drawText(layout, color = labelColor, topLeft = topLeft)
                    }
                }
            }
    )
}

/**
 * What to frame, in grid units: the country's core landmasses plus every
 * visited city.
 *
 * "Core" is the largest landmass plus any other that's both sizeable and
 * nearby - so Japan keeps Hokkaido and Kyushu, but France drops French
 * Guiana (15% of the mainland's area, 7,000 km away) and the US drops
 * Hawaii. A visited city there still pulls it into frame.
 */
private fun focusBounds(map: WorldMapData, rings: List<IntArray>, cityPoints: List<Offset>, fallback: Offset): Rect {
    val landmasses = rings.mapNotNull { ring -> ringsBounds(listOf(ring)) }
    val mainland = landmasses.maxByOrNull { it.width * it.height }
    val core = if (mainland == null) {
        emptyList()
    } else {
        val mainlandArea = mainland.width * mainland.height
        val reach = mainland.maxDimension * 1.5f
        landmasses.filter { box ->
            box.width * box.height >= mainlandArea * 0.05f && (box.center - mainland.center).getDistance() <= reach
        }
    }
    var left = core.minOfOrNull { it.left } ?: fallback.x
    var top = core.minOfOrNull { it.top } ?: fallback.y
    var right = core.maxOfOrNull { it.right } ?: fallback.x
    var bottom = core.maxOfOrNull { it.bottom } ?: fallback.y
    for (point in cityPoints) {
        left = min(left, point.x)
        top = min(top, point.y)
        right = max(right, point.x)
        bottom = max(bottom, point.y)
    }
    // A floor on the frame (~200 km across), so Singapore or Malta shows its
    // surroundings but stays big enough to see around its city dot.
    val minExtent = map.width / 180f
    val width = max(right - left, minExtent)
    val height = max(bottom - top, minExtent)
    val center = Offset((left + right) / 2, (top + bottom) / 2)
    return rectAround(center, width / 2, height / 2).let {
        // Margin so coastlines and edge cities don't touch the frame.
        Rect(it.left - width * 0.08f, it.top - height * 0.08f, it.right + width * 0.08f, it.bottom + height * 0.08f)
    }
}

private fun rectAround(center: Offset, halfWidth: Float, halfHeight: Float) =
    Rect(center.x - halfWidth, center.y - halfHeight, center.x + halfWidth, center.y + halfHeight)
