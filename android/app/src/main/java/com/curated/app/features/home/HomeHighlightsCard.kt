package com.curated.app.features.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.curated.app.core.map.VisitedWorldMap
import com.curated.app.core.map.WorldMapData
import com.curated.app.core.map.WorldMaps
import com.curated.app.core.map.statsFor
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.HairlineCard

/**
 * The personal strip above the feed - the reason to open the app on a day when
 * you aren't going anywhere.
 *
 * The map only appears once there's at least one country on it. An empty world
 * outline reads as an accusation rather than an achievement, which is exactly
 * the wrong first impression for somebody who hasn't travelled yet.
 */
@Composable
fun HomeHighlightsCard(
    highlights: HomeHighlights,
    onOpenTrip: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (highlights.visits.isEmpty() && highlights.onThisDay == null) return

    val context = LocalContext.current
    val map by produceState<WorldMapData?>(initialValue = null) {
        value = runCatching { WorldMaps.load(context) }.getOrNull()
    }
    val loadedMap = map
    val stats = loadedMap?.statsFor(highlights.visits)

    HairlineCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            highlights.onThisDay?.let { memory ->
                Text(
                    "On this day, ${memory.yearsAgo} ${if (memory.yearsAgo == 1) "year" else "years"} ago: " +
                        "${memory.title} · ${memory.destination}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onOpenTrip(memory.tripId) }
                )
            }

            if (stats != null && stats.countries > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Figure("${stats.countries}", if (stats.countries == 1) "country" else "countries")
                    Figure("${stats.worldPercent}%", "of the world")
                    if (highlights.savedPlaceCount > 0) {
                        Figure(
                            "${highlights.savedPlaceCount}",
                            if (highlights.savedPlaceCount == 1) "place saved" else "places saved"
                        )
                    }
                }
                // Compact: this is a glance on the way to the feed, not the profile's centrepiece.
                VisitedWorldMap(map = loadedMap, visits = highlights.visits)
            }
        }
    }
}

@Composable
private fun Figure(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
