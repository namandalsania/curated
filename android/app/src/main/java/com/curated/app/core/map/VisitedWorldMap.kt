package com.curated.app.core.map

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A flat, tile-free world map with visited countries filled in the accent
 * color — the Stippl-style "where I've been" view. Not interactive: it's a
 * summary, and a pannable map inside a scrolling profile fights the scroll.
 *
 * Countries too small to see at this size (Monaco, Vatican City, ...) are
 * drawn as a dot at the visit's location instead of silently disappearing.
 */
@Composable
fun VisitedWorldMap(
    map: WorldMapData,
    visits: List<CountryVisit>,
    modifier: Modifier = Modifier
) {
    val landColor = MaterialTheme.colorScheme.outlineVariant
    val visitedColor = MaterialTheme.colorScheme.primary
    val borderColor = MaterialTheme.colorScheme.surface
    val visitedCodes = visits.mapNotNullTo(HashSet()) { it.countryCode }
    val description = if (visits.isEmpty()) {
        "World map, no countries visited yet"
    } else {
        "World map with ${visits.size} visited ${if (visits.size == 1) "country" else "countries"}: " +
            visits.joinToString { it.country }
    }

    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(map.aspectRatio)
            .semantics { contentDescription = description }
            .drawWithCache {
                val scale = size.width / map.width
                val land = Path()
                val visited = Path()
                for ((code, rings) in map.outlines) {
                    (if (code in visitedCodes) visited else land).addRings(rings, scale)
                }
                // Monaco or Vatican City has an outline but it's under a pixel at
                // phone width, so "has an outline" isn't enough - dot anything
                // whose filled shape would be too small to see.
                val minVisible = 4.dp.toPx()
                val dots = visits
                    .filter { visit ->
                        val rings = visit.countryCode?.let { map.outlines[it] }
                        val bounds = rings?.let(::ringsBounds)
                        bounds == null || bounds.maxDimension * scale < minVisible
                    }
                    .map { map.project(it.latitude, it.longitude) * scale }
                val border = Stroke(width = 0.75.dp.toPx(), join = StrokeJoin.Round)
                val dotRadius = 3.dp.toPx()
                val dotRing = 1.5.dp.toPx()

                onDrawBehind {
                    drawPath(land, landColor)
                    drawPath(land, borderColor, style = border)
                    drawPath(visited, visitedColor)
                    drawPath(visited, borderColor, style = border)
                    for (dot in dots) {
                        drawCircle(borderColor, radius = dotRadius + dotRing, center = dot)
                        drawCircle(visitedColor, radius = dotRadius, center = dot)
                    }
                }
            }
    )
}

/** Headline numbers for a set of visits: countries, cities, continents, share of the world. */
data class TravelStats(val countries: Int, val cities: Int, val continents: Int, val worldPercent: Int)

fun WorldMapData.statsFor(visits: List<CountryVisit>): TravelStats {
    val continents = visits.mapNotNullTo(HashSet()) { visit -> visit.countryCode?.let { continentOf[it] } }
    val percent = visits.size * 100.0 / WORLD_COUNTRY_COUNT
    return TravelStats(
        countries = visits.size,
        cities = visits.sumOf { it.cities.size },
        continents = continents.size,
        // Never show "0%" for someone who has been somewhere.
        worldPercent = if (visits.isEmpty()) 0 else percent.toInt().coerceAtLeast(1)
    )
}
