package com.curated.app.features.explore

import com.curated.app.core.model.BudgetTag
import com.curated.app.core.model.SeasonTag
import com.curated.app.core.model.Trip
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class ExploreFiltersTest {

    private val created = Instant.parse("2026-09-01T00:00:00Z")

    private fun trip(days: Int) = Trip(
        id = "t$days", authorId = "a", title = "T", destination = "D",
        startDate = LocalDate(2026, 9, 1),
        endDate = LocalDate.fromEpochDays(LocalDate(2026, 9, 1).toEpochDays() + days - 1),
        createdAt = created, updatedAt = created
    )

    // --- toSearchParams ---

    @Test
    fun `no filters and no text ask for everything`() {
        val params = ExploreFilters().toSearchParams(query = "  ", authorIds = null)
        assertNull(params.query)
        assertNull(params.budgetTag)
        assertNull(params.seasonTag)
        assertNull(params.authorIds)
    }

    @Test
    fun `text is trimmed and budget and season pass straight through`() {
        val params = ExploreFilters(budgetTag = BudgetTag.LUXURY, seasonTag = SeasonTag.FALL)
            .toSearchParams(query = " Lisbon ", authorIds = null)
        assertEquals("Lisbon", params.query)
        assertEquals(BudgetTag.LUXURY, params.budgetTag)
        assertEquals(SeasonTag.FALL, params.seasonTag)
    }

    @Test
    fun `following scope narrows to the given authors`() {
        val params = ExploreFilters(followScope = FollowScope.FOLLOWING).toSearchParams("", listOf("a", "b"))
        assertEquals(listOf("a", "b"), params.authorIds)
    }

    @Test
    fun `trip length isn't sent to the database`() {
        val short = ExploreFilters(tripLength = TripLength.SHORT).toSearchParams("", null)
        assertEquals(ExploreFilters().toSearchParams("", null), short)
    }

    // --- keeps (trip length) ---

    @Test
    fun `no length filter keeps every trip`() {
        listOf(1, 3, 4, 7, 8, 20).forEach { assertTrue(ExploreFilters().keeps(trip(it))) }
    }

    @Test
    fun `length buckets meet at 3-4 and 7-8 days`() {
        val short = ExploreFilters(tripLength = TripLength.SHORT)
        val medium = ExploreFilters(tripLength = TripLength.MEDIUM)
        val long = ExploreFilters(tripLength = TripLength.LONG)
        assertTrue(short.keeps(trip(3)))
        assertFalse(short.keeps(trip(4)))
        assertTrue(medium.keeps(trip(4)))
        assertTrue(medium.keeps(trip(7)))
        assertFalse(medium.keeps(trip(8)))
        assertTrue(long.keeps(trip(8)))
        assertFalse(long.keeps(trip(7)))
    }

    // --- resultsKey ---

    @Test
    fun `the same text and filters give the same key, any change a different one`() {
        val filters = ExploreFilters(budgetTag = BudgetTag.BUDGET)
        assertEquals(filters.resultsKey("Lisbon"), filters.resultsKey(" Lisbon "))
        assertNotEquals(filters.resultsKey("Lisbon"), filters.resultsKey("Porto"))
        assertNotEquals(filters.resultsKey("Lisbon"), filters.copy(seasonTag = SeasonTag.WINTER).resultsKey("Lisbon"))
    }

    // --- chip labels ---

    @Test
    fun `an inactive chip is just its name, an active one adds its value`() {
        assertEquals("Duration", chipLabel(PickerFilter.DURATION, ExploreFilters()))
        assertEquals("Duration · 1–3 days", chipLabel(PickerFilter.DURATION, ExploreFilters(tripLength = TripLength.SHORT)))
        assertEquals("Budget · Mid-range", chipLabel(PickerFilter.BUDGET, ExploreFilters(budgetTag = BudgetTag.MID_RANGE)))
        assertEquals("Season · Winter", chipLabel(PickerFilter.SEASON, ExploreFilters(seasonTag = SeasonTag.WINTER)))
    }
}
