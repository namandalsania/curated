package com.curated.app.features.profile

import com.curated.app.core.map.CountryVisit

// What the profile's countries card and its "all countries" sheet show, kept
// apart from the Compose code so it can be tested without a screen.

/** Chips on the card; the rest fold into one "+N more" chip, so the card's height stays put. */
internal const val COUNTRY_CHIP_LIMIT = 5

/** Most trips first; a tie goes to the more recent trip, then the name. */
internal fun rankCountries(visits: List<CountryVisit>): List<CountryVisit> =
    visits.sortedWith(
        compareByDescending<CountryVisit> { it.tripCount }
            // Descending over nulls-first puts countries with no date last.
            .thenByDescending(nullsFirst()) { it.lastTripEnd }
            .thenBy { it.country }
    )

/** The countries chipped on the card, and how many are behind "+N more" (0: no such chip). */
internal data class CountryChips(val shown: List<CountryVisit>, val more: Int)

internal fun countryChips(visits: List<CountryVisit>): CountryChips {
    val ranked = rankCountries(visits)
    return CountryChips(
        shown = ranked.take(COUNTRY_CHIP_LIMIT),
        more = (ranked.size - COUNTRY_CHIP_LIMIT).coerceAtLeast(0)
    )
}

/** One continent's heading in the "all countries" sheet. */
internal data class ContinentGroup(val title: String, val countries: List<CountryVisit>)

private val continentTitles = mapOf(
    "AF" to "Africa",
    "AS" to "Asia",
    "EU" to "Europe",
    "NA" to "North America",
    "SA" to "South America",
    "OC" to "Oceania"
)

/** Shown last, for countries without an ISO code or one the continent table doesn't have. */
internal const val UNPLACED_GROUP_TITLE = "Elsewhere"

/**
 * Every country under its continent: continents with more countries first,
 * countries within one in [rankCountries] order. [continentOf] maps ISO codes
 * to the continent codes in assets/world_map.json.
 */
internal fun groupByContinent(visits: List<CountryVisit>, continentOf: Map<String, String>): List<ContinentGroup> {
    val byTitle = rankCountries(visits).groupBy { visit ->
        visit.countryCode?.let(continentOf::get)?.let(continentTitles::get) ?: UNPLACED_GROUP_TITLE
    }
    val placed = byTitle
        .filterKeys { it != UNPLACED_GROUP_TITLE }
        .map { (title, countries) -> ContinentGroup(title, countries) }
        .sortedWith(compareByDescending<ContinentGroup> { it.countries.size }.thenBy { it.title })
    val unplaced = byTitle[UNPLACED_GROUP_TITLE]?.let { listOf(ContinentGroup(UNPLACED_GROUP_TITLE, it)) }.orEmpty()
    return placed + unplaced
}
