package com.curated.app.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.curated.app.core.format.Noun
import com.curated.app.core.format.countText
import com.curated.app.core.format.flagEmoji
import com.curated.app.core.map.CountryDetailMap
import com.curated.app.core.map.CountryVisit
import com.curated.app.core.map.VisitedWorldMap
import com.curated.app.core.map.WorldMapData
import com.curated.app.core.map.WorldMaps
import com.curated.app.core.map.statsFor
import com.curated.app.designsystem.CuratedCornerRadius
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.HairlineCard
import com.curated.app.designsystem.components.HairlineDivider
import com.curated.app.designsystem.components.SkeletonBox

/** Width / height of assets/world_map.json, so the skeleton matches the map it stands in for. */
private const val MAP_ASPECT_RATIO = 16000f / 7115f

/** Wide enough for most names; a longer one ellipsizes instead of taking the row. */
private val CountryChipMaxWidth = 168.dp

/** City names under a country in the "all countries" sheet before "+N". */
private const val SHEET_CITY_LIMIT = 3

/**
 * Stippl-style "where I've been": headline stats, a flat world map with
 * visited countries filled in, and a chip for each of the top countries.
 * Tapping a chip zooms into that country; "+N more" lists them all.
 */
@Composable
fun VisitedCountriesCard(
    visits: List<CountryVisit>,
    isLoading: Boolean,
    isOwnProfile: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val map by produceState<WorldMapData?>(initialValue = null) {
        value = runCatching { WorldMaps.load(context) }.getOrNull()
    }
    val chips = countryChips(visits)
    var openCountry by remember { mutableStateOf<CountryVisit?>(null) }
    var showAll by remember { mutableStateOf(false) }

    HairlineCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            val loadedMap = map
            // Dashes until both the geocoded visits and the continent table are in.
            val stats = if (isLoading) null else loadedMap?.statsFor(visits)
            Row(modifier = Modifier.fillMaxWidth()) {
                Stat(
                    value = stats?.countries?.toString() ?: "–",
                    label = if (stats?.countries == 1) "country" else "countries",
                    modifier = Modifier.weight(1f)
                )
                Stat(
                    value = stats?.cities?.toString() ?: "–",
                    label = if (stats?.cities == 1) "city" else "cities",
                    modifier = Modifier.weight(1f)
                )
                Stat(
                    value = stats?.continents?.toString() ?: "–",
                    label = if (stats?.continents == 1) "continent" else "continents",
                    modifier = Modifier.weight(1f)
                )
                Stat(
                    value = stats?.let { "${it.worldPercent}%" } ?: "–",
                    label = "of the world",
                    modifier = Modifier.weight(1f)
                )
            }

            if (loadedMap == null || isLoading) {
                SkeletonBox(modifier = Modifier.fillMaxWidth().aspectRatio(MAP_ASPECT_RATIO))
            } else {
                VisitedWorldMap(map = loadedMap, visits = visits)
            }

            when {
                isLoading -> Unit
                visits.isEmpty() -> Text(
                    if (isOwnProfile) {
                        "Publish a trip and the countries you've been to fill in here."
                    } else {
                        "No countries yet."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    chips.shown.forEach { visit ->
                        CountryChip(visit, onClick = { openCountry = visit })
                    }
                    if (chips.more > 0) MoreChip(chips.more, onClick = { showAll = true })
                }
            }
        }
    }

    val loadedMap = map
    if (showAll) {
        ModalBottomSheet(
            onDismissRequest = { showAll = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = CuratedCornerRadius, topEnd = CuratedCornerRadius),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AllCountriesSheetContent(
                // Until the map's continent table is in, everything sits under "Elsewhere".
                groups = groupByContinent(visits, loadedMap?.continentOf.orEmpty()),
                total = visits.size,
                onCountryClick = { visit ->
                    showAll = false
                    openCountry = visit
                }
            )
        }
    }

    val shownCountry = openCountry
    if (shownCountry != null && loadedMap != null) {
        ModalBottomSheet(
            onDismissRequest = { openCountry = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = CuratedCornerRadius, topEnd = CuratedCornerRadius),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            CountrySheetContent(map = loadedMap, visit = shownCountry)
        }
    }
}

@Composable
private fun CountrySheetContent(map: WorldMapData, visit: CountryVisit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .padding(bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                countryTitle(visit),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                buildString {
                    if (visit.cities.isNotEmpty()) {
                        append(visit.cities.size).append(if (visit.cities.size == 1) " city · " else " cities · ")
                    }
                    append(visit.tripCount).append(if (visit.tripCount == 1) " trip" else " trips")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HairlineCard(modifier = Modifier.fillMaxWidth()) {
            CountryDetailMap(map = map, visit = visit)
        }

        if (visit.cities.isEmpty()) {
            Text(
                "We couldn't name the places in ${visit.country}, but it still counts on your map.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column {
                visit.cities.forEachIndexed { index, city ->
                    if (index > 0) HairlineDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm + Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            city.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            countText(city.tripCount, Noun.TRIP),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Every country, under its continent, with its trip count; scrolls inside the sheet. */
@Composable
internal fun AllCountriesSheetContent(
    groups: List<ContinentGroup>,
    total: Int,
    onCountryClick: (CountryVisit) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, bottom = Spacing.lg)
    ) {
        item(key = "title") {
            Text(
                "$total ${if (total == 1) "country" else "countries"} visited",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = Spacing.sm)
            )
        }
        groups.forEach { group ->
            item(key = "continent-${group.title}") {
                Text(
                    group.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs)
                )
            }
            itemsIndexed(group.countries, key = { _, visit -> "country-${visit.countryCode ?: visit.country}" }) { index, visit ->
                if (index > 0) HairlineDivider()
                CountryRow(visit, onClick = { onCountryClick(visit) })
            }
        }
    }
}

/** "🇮🇹 Italy" over "Rome, Florence, Venice +2", and "4 trips" on the right. */
@Composable
private fun CountryRow(visit: CountryVisit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Show ${visit.country}", onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                countryTitle(visit),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (visit.cities.isNotEmpty()) {
                Text(
                    buildString {
                        append(visit.cities.take(SHEET_CITY_LIMIT).joinToString { it.name })
                        val more = visit.cities.size - SHEET_CITY_LIMIT
                        if (more > 0) append(" +").append(more)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            countText(visit.tripCount, Noun.TRIP),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.sm)
        )
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** "🇮🇹 Italy" - the country only; its cities are in the sheet. */
@Composable
private fun CountryChip(visit: CountryVisit, onClick: () -> Unit) {
    ChipText(countryTitle(visit), onClickLabel = "Show ${visit.country}", onClick = onClick)
}

/** "+20 more" - opens every country, by continent. */
@Composable
private fun MoreChip(more: Int, onClick: () -> Unit) {
    ChipText("+$more more", onClickLabel = "Show all countries", onClick = onClick)
}

@Composable
private fun ChipText(label: String, onClickLabel: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = CountryChipMaxWidth)
            .clip(RoundedCornerShape(CuratedCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = onClickLabel, onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = 6.dp)
    )
}

/** "🇮🇹 Italy", or just the name when there's no usable ISO code. */
private fun countryTitle(visit: CountryVisit): String {
    val flag = visit.countryCode?.let(::flagEmoji)
    return if (flag != null) "$flag ${visit.country}" else visit.country
}
