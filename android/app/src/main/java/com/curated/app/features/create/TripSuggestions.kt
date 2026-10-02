package com.curated.app.features.create

import com.curated.app.core.geocode.PlaceRef
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * What a new trip can be pre-filled with, so nobody starts from blank fields:
 * a title from where and when, dates from when the photos were taken, and a
 * destination from where they were taken.
 */
object TripSuggestions {

    /**
     * "Lisbon · October 2026". Only the first part of the destination is used -
     * "Lisbon, Portugal" would make a long title out of what's already shown
     * under it. Trips that cross months name both: "Lisbon · September – October 2026".
     * Empty when there's no destination yet.
     */
    fun title(destination: String, start: LocalDate?, end: LocalDate? = null): String {
        val place = destination.substringBefore(',').trim()
        if (place.isEmpty()) return ""
        val first = start ?: return place
        val last = end?.takeIf { it >= first } ?: first
        val month = { date: LocalDate -> date.month.name.lowercase().replaceFirstChar { it.uppercase() } }
        val period = when {
            first.year != last.year -> "${month(first)} ${first.year} – ${month(last)} ${last.year}"
            first.month != last.month -> "${month(first)} – ${month(last)} ${last.year}"
            else -> "${month(first)} ${first.year}"
        }
        return "$place · $period"
    }

    /** The first and last day the photos were taken on, or null when none of them say. */
    fun dateRange(takenAt: List<Instant?>, timeZone: TimeZone): Pair<LocalDate, LocalDate>? {
        val dates = takenAt.filterNotNull().map { it.toLocalDateTime(timeZone).date }
        val first = dates.minOrNull() ?: return null
        return first to dates.max()
    }

    /**
     * Where a set of stops was, in one line. A city when most of the stops are
     * in it ("Lisbon, Portugal"); the country when they're spread across a
     * country ("Japan" for Tokyo and Kyoto); empty when nothing could be placed.
     */
    fun destination(places: List<PlaceRef>): String {
        if (places.isEmpty()) return ""
        val country = places.groupingBy { it.country.code ?: it.country.name }.eachCount().maxBy { it.value }.key
        val inCountry = places.filter { (it.country.code ?: it.country.name) == country }
        val countryName = inCountry.first().country.name
        val topCity = inCountry.mapNotNull { it.city }.groupingBy { it }.eachCount().maxByOrNull { it.value }
        return if (topCity != null && topCity.value * 2 >= places.size) {
            "${topCity.key}, $countryName"
        } else {
            countryName
        }
    }
}
