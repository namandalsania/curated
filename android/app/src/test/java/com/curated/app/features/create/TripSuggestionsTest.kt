package com.curated.app.features.create

import com.curated.app.core.geocode.CountryRef
import com.curated.app.core.geocode.PlaceRef
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripSuggestionsTest {

    private val portugal = CountryRef("PT", "Portugal")
    private val japan = CountryRef("JP", "Japan")

    // --- title ---

    @Test
    fun `title is the place and the month`() {
        assertEquals("Lisbon · October 2026", TripSuggestions.title("Lisbon", LocalDate(2026, 10, 1)))
    }

    @Test
    fun `title keeps only the place before the first comma`() {
        assertEquals("Lisbon · October 2026", TripSuggestions.title("Lisbon, Portugal", LocalDate(2026, 10, 1)))
    }

    @Test
    fun `title names both months when the trip crosses one`() {
        assertEquals(
            "Lisbon · September – October 2026",
            TripSuggestions.title("Lisbon", LocalDate(2026, 9, 28), LocalDate(2026, 10, 3))
        )
    }

    @Test
    fun `title names both years when the trip crosses one`() {
        assertEquals(
            "Tokyo · December 2025 – January 2026",
            TripSuggestions.title("Tokyo", LocalDate(2025, 12, 30), LocalDate(2026, 1, 2))
        )
    }

    @Test
    fun `title ignores an end date before the start`() {
        assertEquals(
            "Lisbon · October 2026",
            TripSuggestions.title("Lisbon", LocalDate(2026, 10, 5), LocalDate(2026, 9, 1))
        )
    }

    @Test
    fun `title is just the place without dates, and empty without a place`() {
        assertEquals("Lisbon", TripSuggestions.title("Lisbon", null))
        assertEquals("", TripSuggestions.title("  ", LocalDate(2026, 10, 1)))
    }

    // --- dateRange ---

    @Test
    fun `date range runs from the earliest photo to the latest, in local time`() {
        val range = TripSuggestions.dateRange(
            listOf(
                Instant.parse("2026-09-14T10:00:00Z"),
                null,
                Instant.parse("2026-09-12T23:30:00Z"),
                Instant.parse("2026-09-16T08:00:00Z")
            ),
            TimeZone.of("Asia/Tokyo")
        )
        // 23:30 UTC on the 12th is the 13th in Tokyo.
        assertEquals(LocalDate(2026, 9, 13) to LocalDate(2026, 9, 16), range)
    }

    @Test
    fun `date range is null when no photo has a time`() {
        assertNull(TripSuggestions.dateRange(listOf(null, null), TimeZone.UTC))
    }

    // --- destination ---

    @Test
    fun `destination is the city most stops are in`() {
        val places = listOf(
            PlaceRef(portugal, "Lisbon"),
            PlaceRef(portugal, "Lisbon"),
            PlaceRef(portugal, "Sintra")
        )
        assertEquals("Lisbon, Portugal", TripSuggestions.destination(places))
    }

    @Test
    fun `destination is the country when no city has most of the stops`() {
        val places = listOf(
            PlaceRef(japan, "Tokyo"),
            PlaceRef(japan, "Kyoto"),
            PlaceRef(japan, "Osaka")
        )
        assertEquals("Japan", TripSuggestions.destination(places))
    }

    @Test
    fun `destination follows the country most stops are in`() {
        val places = listOf(
            PlaceRef(japan, "Tokyo"),
            PlaceRef(japan, null),
            PlaceRef(portugal, "Lisbon")
        )
        assertEquals("Japan", TripSuggestions.destination(places))
    }

    @Test
    fun `destination is empty when nothing could be placed`() {
        assertEquals("", TripSuggestions.destination(emptyList()))
    }
}
