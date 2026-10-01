package com.curated.app.features.create

import com.curated.app.core.data.StopWithPhotos
import com.curated.app.core.data.TripDaySection
import com.curated.app.core.model.Day
import com.curated.app.core.model.DayPublishSource
import com.curated.app.core.model.Stop
import com.curated.app.core.model.StopCategory
import com.curated.app.core.trip.deriveCover
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTripRulesTest {

    private val start = LocalDate(2026, 9, 20)
    private val created = Instant.parse("2026-09-20T08:00:00Z")

    private fun stop(id: String, dayId: String?, category: StopCategory = StopCategory.SIGHT) = Stop(
        id = id, dayId = dayId, tripId = "t", name = "Place $id", category = category,
        latitude = 0.0, longitude = 0.0, orderInDay = 0, createdAt = created
    )

    private fun day(index: Int, posted: Boolean = false) = Day(
        id = "day-$index", tripId = "t", dayIndex = index,
        publishedAt = if (posted) created else null,
        publishedVia = if (posted) DayPublishSource.POST_DAY else null
    )

    private fun section(index: Int?, vararg stops: Stop, photos: List<String> = emptyList()) =
        TripDaySection(index, null, stops.map { StopWithPhotos(it, photos) })

    // --- dayCount ---

    @Test
    fun `day count runs from the start date to today inclusive`() {
        assertEquals(3, LiveTripRules.dayCount(start, LocalDate(2026, 9, 22), emptyList(), emptyList()))
    }

    @Test
    fun `day count never hides a day that already has stops`() {
        val sections = listOf(section(5, stop("a", "day-5")))
        assertEquals(5, LiveTripRules.dayCount(start, LocalDate(2026, 9, 21), emptyList(), sections))
    }

    @Test
    fun `day count is at least one even before the trip starts`() {
        assertEquals(1, LiveTripRules.dayCount(start, LocalDate(2026, 9, 18), emptyList(), emptyList()))
    }

    // --- postProblem ---

    @Test
    fun `an empty day cannot be posted`() {
        assertEquals(LiveTripRules.PostProblem.NoStops, LiveTripRules.postProblem(emptyList()))
    }

    @Test
    fun `a stop without a day blocks posting and is named`() {
        val problem = LiveTripRules.postProblem(listOf(stop("a", "day-1"), stop("b", null)))
        assertTrue(problem is LiveTripRules.PostProblem.Unassigned)
        assertEquals(listOf("b"), (problem as LiveTripRules.PostProblem.Unassigned).stops.map { it.id })
    }

    @Test
    fun `a day whose stops all have a day can be posted`() {
        assertNull(LiveTripRules.postProblem(listOf(stop("a", "day-1"), stop("b", "day-1"))))
    }

    // --- splitByDay ---

    @Test
    fun `only photos taken on the day are kept, undated ones get the benefit of the doubt`() {
        val photos = listOf(
            "same" to Instant.parse("2026-09-21T10:00:00Z"),
            "other" to Instant.parse("2026-09-20T10:00:00Z"),
            "undated" to null
        )
        val (kept, skipped) = LiveTripRules.splitByDay(photos, LocalDate(2026, 9, 21), TimeZone.UTC) { it.second }
        assertEquals(listOf("same", "undated"), kept.map { it.first })
        assertEquals(listOf("other"), skipped.map { it.first })
    }

    @Test
    fun `the day boundary follows the given time zone`() {
        // 23:30 UTC on the 20th is already the 21st in Bangkok.
        val photo = listOf("late" to Instant.parse("2026-09-20T23:30:00Z"))
        val (kept, _) = LiveTripRules.splitByDay(photo, LocalDate(2026, 9, 21), TimeZone.of("Asia/Bangkok")) { it.second }
        assertEquals(1, kept.size)
    }

    // --- End trip ---

    @Test
    fun `end trip publishes unposted days that have stops, and only those`() {
        val days = listOf(day(1, posted = true), day(2), day(3), day(4))
        val sections = listOf(
            section(1, stop("a", "day-1")),
            section(2, stop("b", "day-2")),
            // day 3 exists but is empty
            section(4, stop("c", "day-4"))
        )
        assertEquals(listOf(2, 4), LiveTripRules.daysEndTripWillPublish(days, sections))
    }

    @Test
    fun `the trip ends on its last day with stops`() {
        val sections = listOf(section(1, stop("a", "day-1")), section(3, stop("b", "day-3")), section(4))
        assertEquals(LocalDate(2026, 9, 22), LiveTripRules.endDate(start, sections))
    }

    // --- deriveCover ---

    @Test
    fun `cover skips the hotel when another stop has a photo`() {
        val stops = listOf(
            StopWithPhotos(stop("h", "d", StopCategory.HOTEL), listOf("hotel.jpg")),
            StopWithPhotos(stop("s", "d"), listOf("sight.jpg"))
        )
        assertEquals("sight.jpg", deriveCover(stops))
    }

    @Test
    fun `cover falls back to the hotel when it is the only photo`() {
        val stops = listOf(
            StopWithPhotos(stop("h", "d", StopCategory.HOTEL), listOf("hotel.jpg")),
            StopWithPhotos(stop("s", "d"), emptyList())
        )
        assertEquals("hotel.jpg", deriveCover(stops))
    }

    @Test
    fun `no photos means no cover`() {
        assertNull(deriveCover(listOf(StopWithPhotos(stop("s", "d"), emptyList()))))
    }
}
